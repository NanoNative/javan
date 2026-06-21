package javan.codegen;

import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the tiny C runtime used by generated programs.
 */
public final class RuntimeFiles {
    private static final String HEADER = """
        #ifndef JAVAN_RUNTIME_H
        #define JAVAN_RUNTIME_H

        #include <setjmp.h>

        typedef struct JavanSourceContext {
            const char* code;
            const char* summary;
            const char* class_name;
            const char* method;
            const char* file;
            int line;
            int bytecode_offset;
            const char* source_line;
            const char* why;
            const char* fix;
            struct JavanSourceContext* previous;
        } JavanSourceContext;

        void javan_println(const char* value);
        void javan_print(const char* value);
        void javan_eprintln(const char* value);
        void javan_eprint(const char* value);
        void* javan_system_out(void);
        void* javan_system_err(void);
        void javan_printstream_print(void* stream, const char* value);
        void javan_printstream_println(void* stream, const char* value);
        void javan_printstream_println_int(void* stream, int value);
        void javan_printstream_println_long(void* stream, long long value);
        void javan_printstream_println_float(void* stream, float value);
        void javan_printstream_println_double(void* stream, double value);
        void javan_printstream_println_bool(void* stream, int value);
        void javan_println_int(int value);
        void javan_eprintln_int(int value);
        void javan_println_long(long long value);
        void javan_eprintln_long(long long value);
        void javan_println_float(float value);
        void javan_eprintln_float(float value);
        void javan_println_double(double value);
        void javan_eprintln_double(double value);
        void javan_println_bool(int value);
        void javan_eprintln_bool(int value);
        int javan_math_abs_int(int value);
        long long javan_math_abs_long(long long value);
        int javan_math_min_int(int left, int right);
        long long javan_math_min_long(long long left, long long right);
        int javan_math_max_int(int left, int right);
        long long javan_math_max_long(long long left, long long right);
        int javan_math_to_int_exact(long long value);
        int javan_int_shl(int value, int shift);
        long long javan_long_shl(long long value, int shift);
        int javan_int_shr(int value, int shift);
        long long javan_long_shr(long long value, int shift);
        int javan_int_ushr(int value, int shift);
        long long javan_long_ushr(long long value, int shift);
        long long javan_i2l(int value);
        float javan_i2f(int value);
        double javan_i2d(int value);
        int javan_l2i(long long value);
        int javan_i2b(int value);
        int javan_i2s(int value);
        long long javan_system_nano_time(void);
        long long javan_system_current_time_millis(void);
        void* javan_system_line_separator(void);
        void* javan_system_getenv(const char* name);
        void* javan_system_get_property(const char* key);
        void* javan_system_get_property_or_default(const char* key, const char* fallback);
        void javan_system_arraycopy(void* source, int source_position, void* target, int target_position, int length);
        void javan_system_exit(int status);
        int javan_file_separator_char(void);
        int javan_file_path_separator_char(void);
        void* javan_file_path_separator(void);
        void* javan_objects_require_non_null(void* value);
        void* javan_objects_require_non_null_msg(void* value, const char* message);
        typedef struct {
            signed char* data;
            int length;
        } JavanByteArray;
        typedef struct {
            int ok;
            char* code;
            char* message;
            char* summary;
            char* class_name;
            char* method;
            char* file;
            int line;
            int bytecode_offset;
            char* source_line;
            char* why;
            char* fix;
            char* detail;
        } JavanResult;
        typedef struct {
            int type_id;
            const char* name;
            int object_field_count;
            unsigned long* object_field_offsets;
        } JavanTypeDescriptor;
        void* javan_alloc(unsigned long size);
        void javan_free(void* value);
        JavanResult javan_result_ok(void);
        JavanResult javan_result_error_from_last_error(void);
        JavanResult javan_result_error_message(const char* code, const char* summary, const char* detail);
        void javan_result_free(JavanResult* result);
        void javan_register_type_descriptors(JavanTypeDescriptor* descriptors, int count);
        void javan_register_static_roots(void*** roots, int count);
        void javan_root_frame_push(void*** roots, int count);
        void javan_root_frame_pop(void*** roots);
        void javan_register_object(void* value, int type_id);
        void javan_validate_heap_metadata(void);
        void javan_gc_safe_point(void);
        void javan_gc_collect(void);
        unsigned long javan_heap_live_allocations(void);
        unsigned long javan_heap_live_bytes(void);
        unsigned long javan_heap_total_allocations(void);
        unsigned long javan_heap_total_allocated_bytes(void);
        unsigned long javan_heap_peak_live_bytes(void);
        unsigned long javan_heap_gc_collections(void);
        unsigned long javan_heap_gc_collected_allocations(void);
        unsigned long javan_heap_gc_collected_bytes(void);
        int javan_heap_type_descriptor_count(void);
        int javan_heap_static_root_count(void);
        int javan_heap_root_frame_depth(void);
        int javan_heap_frame_root_count(void);
        int javan_object_non_null(void* value);
        int javan_object_type_in(void* value, int count, ...);
        void* javan_object_array_new(int length);
        void* javan_object_array_get(void* array, int index);
        void javan_object_array_set(void* array, int index, void* value);
        void* javan_int_array_new(int length);
        int javan_int_array_get(void* array, int index);
        void javan_int_array_set(void* array, int index, int value);
        void* javan_long_array_new(int length);
        long long javan_long_array_get(void* array, int index);
        void javan_long_array_set(void* array, int index, long long value);
        void* javan_float_array_new(int length);
        float javan_float_array_get(void* array, int index);
        void javan_float_array_set(void* array, int index, float value);
        void* javan_double_array_new(int length);
        double javan_double_array_get(void* array, int index);
        void javan_double_array_set(void* array, int index, double value);
        void* javan_byte_array_new(int length);
        void* javan_boolean_array_new(int length);
        int javan_byte_array_get(void* array, int index);
        void javan_byte_array_set(void* array, int index, int value);
        void* javan_byte_array_from(const signed char* data, int length);
        JavanByteArray javan_byte_array_export(void* array);
        void* javan_short_array_new(int length);
        int javan_short_array_get(void* array, int index);
        void javan_short_array_set(void* array, int index, int value);
        void* javan_char_array_new(int length);
        int javan_char_array_get(void* array, int index);
        void javan_char_array_set(void* array, int index, int value);
        int javan_array_length(void* array);
        void* javan_arrays_copy_of_object(void* array, int new_length);
        void* javan_arrays_copy_of_int(void* array, int new_length);
        void* javan_arrays_copy_of_long(void* array, int new_length);
        void* javan_arrays_copy_of_float(void* array, int new_length);
        void* javan_arrays_copy_of_double(void* array, int new_length);
        void* javan_arrays_copy_of_byte(void* array, int new_length);
        void* javan_arrays_copy_of_short(void* array, int new_length);
        void* javan_arrays_copy_of_char(void* array, int new_length);
        void* javan_arrays_copy_of_range_byte(void* array, int begin, int end);
        void* javan_arrays_copy_of_range_object(void* array, int begin, int end);
        void* javan_string_array_from_args(int argc, char** argv);
        void* javan_string_from(const char* value);
        void* javan_string_from_chars(void* array, int offset, int count);
        int javan_string_length(const char* value);
        int javan_string_is_empty(const char* value);
        int javan_string_char_at(const char* value, int index);
        int javan_string_index_of_char(const char* value, int ch);
        int javan_string_index_of_char_from(const char* value, int ch, int from_index);
        int javan_string_index_of_string(const char* value, const char* needle);
        int javan_string_index_of_string_from(const char* value, const char* needle, int from_index);
        int javan_string_last_index_of_char(const char* value, int ch);
        int javan_string_last_index_of_char_from(const char* value, int ch, int from_index);
        int javan_string_equals(const char* left, const char* right);
        int javan_string_contains(const char* left, const char* right);
        int javan_string_starts_with(const char* left, const char* prefix);
        int javan_string_ends_with(const char* left, const char* suffix);
        void* javan_string_replace_char(const char* value, int old_ch, int new_ch);
        void* javan_string_trim(const char* value);
        void* javan_string_substring(const char* value, int begin);
        void* javan_string_substring_range(const char* value, int begin, int end);
        void* javan_arraylist_new(void);
        int javan_arraylist_add(void* list, void* value);
        void javan_arraylist_add_at(void* list, int index, void* value);
        int javan_arraylist_add_all(void* list, void* collection);
        void javan_arraylist_add_first(void* list, void* value);
        void* javan_arraylist_set(void* list, int index, void* value);
        void* javan_arraylist_remove_last(void* list);
        void* javan_list_of(int count, ...);
        void* javan_list_of_array(void* array);
        void* javan_list_copy_of(void* collection);
        int javan_list_size(void* list);
        int javan_list_is_empty(void* list);
        int javan_list_contains(void* list, void* value);
        void* javan_list_get(void* list, int index);
        void* javan_list_get_first(void* list);
        void* javan_list_get_last(void* list);
        void* javan_list_iterator(void* list);
        int javan_iterator_has_next(void* iterator);
        void* javan_iterator_next(void* iterator);
        void* javan_hashmap_new(void);
        void* javan_map_copy_of(void* map);
        void* javan_map_get(void* map, void* key);
        void* javan_map_get_or_default(void* map, void* key, void* fallback);
        void* javan_map_put(void* map, void* key, void* value);
        void* javan_map_put_if_absent(void* map, void* key, void* value);
        int javan_map_contains_key(void* map, void* key);
        int javan_map_size(void* map);
        int javan_map_is_empty(void* map);
        void* javan_map_values(void* map);
        void* javan_path_of(void* first, void* more);
        void* javan_path_resolve(void* path, void* child);
        void* javan_path_to_absolute(void* path);
        void* javan_path_normalize(void* path);
        void* javan_path_get_parent(void* path);
        void* javan_path_get_file_name(void* path);
        void* javan_path_relativize(void* path, void* child);
        int javan_path_starts_with(void* path, void* prefix);
        int javan_path_equals(void* path, void* other);
        int javan_path_is_absolute(void* path);
        int javan_path_get_name_count(void* path);
        void* javan_path_get_name(void* path, int index);
        void* javan_process_run(void* cwd, void* command, long long timeout_millis);
        int javan_process_result_exit_code(void* value);
        void* javan_process_result_stdout(void* value);
        void* javan_process_result_stderr(void* value);
        int javan_files_exists(void* path, void* options);
        int javan_files_is_directory(void* path, void* options);
        int javan_files_is_regular_file(void* path, void* options);
        int javan_files_is_executable(void* path);
        void* javan_files_create_directories(void* path, void* attributes);
        void* javan_files_copy(void* source, void* target, void* options);
        void* javan_files_read_string(void* path);
        void* javan_files_write_string(void* path, void* value, void* options);
        void* javan_files_write_bytes(void* path, void* bytes, void* options);
        void* javan_files_read_all_bytes(void* path);
        int javan_files_delete_if_exists(void* path);
        long long javan_files_size(void* path);
        void* javan_files_get_last_modified_time(void* path, void* options);
        long long javan_file_time_to_millis(void* value);
        void* javan_files_new_directory_stream(void* path);
        void* javan_inet_address_loopback(void);
        void* javan_inet_address_get_host_address(void* value);
        void* javan_inet_address_get_host_name(void* value);
        void* javan_inet_address_get_canonical_host_name(void* value);
        void* javan_inet_socket_address_from_host(void* host, int port);
        void* javan_inet_socket_address_from_address(void* address, int port);
        int javan_inet_socket_address_get_port(void* value);
        void* javan_inet_socket_address_get_host_string(void* value);
        void* javan_inet_socket_address_get_address(void* value);
        void* javan_inet_socket_address_to_string(void* value);
        void* javan_socket_connect_host(void* host, int port);
        int javan_socket_is_connected(void* value);
        int javan_socket_is_closed(void* value);
        int javan_socket_get_port(void* value);
        int javan_socket_get_local_port(void* value);
        void* javan_socket_get_inet_address(void* value);
        void* javan_socket_input_stream(void* value);
        void* javan_socket_output_stream(void* value);
        int javan_socket_input_stream_read(void* value);
        int javan_socket_input_stream_read_bytes(void* value, void* bytes);
        int javan_socket_input_stream_read_bytes_range(void* value, void* bytes, int offset, int length);
        void javan_socket_input_stream_close(void* value);
        void javan_socket_output_stream_write(void* value, int byte_value);
        void javan_socket_output_stream_write_bytes(void* value, void* bytes);
        void javan_socket_output_stream_write_bytes_range(void* value, void* bytes, int offset, int length);
        void javan_socket_output_stream_flush(void* value);
        void javan_socket_output_stream_close(void* value);
        void javan_socket_close(void* value);
        void* javan_server_socket_bind(int port);
        int javan_server_socket_get_local_port(void* value);
        void* javan_server_socket_accept(void* value);
        void javan_server_socket_close(void* value);
        void* javan_uri_create(void* value);
        void* javan_http_client_new(void);
        void* javan_http_request_builder_new(void* uri);
        void* javan_http_request_builder_get(void* value);
        void* javan_http_request_builder_header(void* value, void* name, void* header_value);
        void* javan_http_request_builder_post(void* value, void* body_publisher);
        void* javan_http_request_builder_put(void* value, void* body_publisher);
        void* javan_http_request_builder_build(void* value);
        void* javan_http_body_publisher_string(void* value);
        void* javan_http_body_publisher_byte_array(void* value);
        void* javan_http_body_handler_string(void);
        void* javan_http_body_handler_byte_array(void);
        void* javan_http_client_send(void* client, void* request, void* body_handler);
        int javan_http_response_status_code(void* response);
        void* javan_http_response_body(void* response);
        void* javan_optional_empty(void);
        void* javan_optional_of(void* value);
        void* javan_optional_of_nullable(void* value);
        int javan_optional_is_present(void* optional);
        int javan_optional_is_empty(void* optional);
        void* javan_optional_or_else(void* optional, void* fallback);
        void* javan_optional_or_else_throw(void* optional);
        void* javan_integer_value_of(int value);
        int javan_integer_int_value(void* value);
        void* javan_long_value_of(long long value);
        long long javan_long_long_value(void* value);
        void* javan_float_value_of(float value);
        float javan_float_float_value(void* value);
        float javan_float_int_bits_to_float(int value);
        void* javan_double_value_of(double value);
        double javan_double_double_value(void* value);
        double javan_double_long_bits_to_double(long long value);
        void* javan_boolean_value_of(int value);
        int javan_boolean_boolean_value(void* value);
        void* javan_duration_of_millis(long long millis);
        void* javan_duration_of_seconds(long long seconds);
        long long javan_duration_to_millis(void* value);
        void* javan_thread_new(void);
        void* javan_thread_current(void);
        void javan_thread_sleep_millis(long long millis);
        int javan_thread_interrupted(void);
        void javan_thread_interrupt(void* value);
        int javan_thread_is_interrupted(void* value);
        void* javan_string_value_of_int(int value);
        void* javan_string_value_of_long(long long value);
        void* javan_string_value_of_float(float value);
        void* javan_string_value_of_double(double value);
        void* javan_string_value_of_bool(int value);
        void* javan_string_value_of_char(int value);
        void* javan_string_concat(const char* recipe, int argc, const char** values);
        char* javan_string_export(const char* value);
        void* javan_stringbuilder_new(void);
        void* javan_stringbuilder_append_string(void* builder, void* value);
        void* javan_stringbuilder_append_object(void* builder, void* value);
        void* javan_stringbuilder_append_boolean(void* builder, int value);
        void* javan_stringbuilder_append_char(void* builder, int value);
        void* javan_stringbuilder_append_int(void* builder, int value);
        void* javan_stringbuilder_append_long(void* builder, long long value);
        void* javan_stringbuilder_to_string(void* builder);
        int javan_stringbuilder_length(void* builder);
        int javan_stringbuilder_is_empty(void* builder);
        void javan_stringbuilder_set_length(void* builder, int length);
        int javan_lcmp(long long left, long long right);
        int javan_float_compare(float left, float right, int nan_value);
        int javan_double_compare(double left, double right, int nan_value);
        const char* javan_last_error(void);
        const char* javan_last_error_code(void);
        const char* javan_last_error_summary(void);
        const char* javan_last_error_class(void);
        const char* javan_last_error_method(void);
        const char* javan_last_error_file(void);
        int javan_last_error_line(void);
        int javan_last_error_bytecode_offset(void);
        const char* javan_last_error_source_line(void);
        const char* javan_last_error_why(void);
        const char* javan_last_error_fix(void);
        const char* javan_last_error_detail(void);
        void javan_clear_error(void);
        void javan_panic_set_target(jmp_buf* target);
        void javan_panic_clear_target(jmp_buf* target);
        void javan_source_enter(
            JavanSourceContext* context,
            const char* code,
            const char* summary,
            const char* class_name,
            const char* method,
            const char* file,
            int line,
            int bytecode_offset,
            const char* source_line,
            const char* why,
            const char* fix
        );
        void javan_source_clear(JavanSourceContext* context);
        void javan_panic(const char* value);
        void javan_panic_at(
            const char* code,
            const char* summary,
            const char* class_name,
            const char* method,
            const char* file,
            int line,
            int bytecode_offset,
            const char* source_line,
            const char* why,
            const char* fix,
            const char* detail
        );

        #endif
        """;

    private static final String SOURCE_MAIN = """
        #include "javan_runtime.h"
        #include <dirent.h>
        #include <errno.h>
        #include <limits.h>
        #include <math.h>
        #include <signal.h>
        #include <setjmp.h>
        #include <stdarg.h>
        #include <stdint.h>
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        #if defined(_WIN32)
        #include <winsock2.h>
        #include <ws2tcpip.h>
        #else
        #include <arpa/inet.h>
        #include <netinet/in.h>
        #include <sys/socket.h>
        #endif
        #include <sys/stat.h>
        #include <sys/time.h>
        #include <sys/wait.h>
        #include <time.h>
        #include <unistd.h>

        static char* javan_string_alloc(unsigned long size);
        static int javan_socket_native_close(int fd);
        static char javan_last_error_value[512];
        static char javan_last_error_code_value[64];
        static char javan_last_error_summary_value[128];
        static char javan_last_error_class_value[160];
        static char javan_last_error_method_value[160];
        static char javan_last_error_file_value[160];
        static char javan_last_error_source_line_value[256];
        static char javan_last_error_why_value[256];
        static char javan_last_error_fix_value[256];
        static char javan_last_error_detail_value[256];
        static int javan_last_error_line_value = -1;
        static int javan_last_error_bytecode_offset_value = -1;
        static int javan_last_error_set = 0;
        static jmp_buf* javan_panic_target = NULL;
        static JavanSourceContext* javan_source_context_top = NULL;

        static void javan_copy_error_field(char* target, unsigned long target_size, const char* value) {
            if (target_size == 0) {
                return;
            }
            if (value == NULL || value[0] == '\\0') {
                target[0] = '\\0';
                return;
            }
            unsigned long length = strlen(value);
            if (length >= target_size) {
                length = target_size - 1;
            }
            memcpy(target, value, length);
            target[length] = '\\0';
        }

        static const char* javan_last_error_field(const char* value) {
            if (javan_last_error_set == 0 || value == NULL || value[0] == '\\0') {
                return NULL;
            }
            return value;
        }

        static void javan_clear_error_fields(void) {
            javan_last_error_value[0] = '\\0';
            javan_last_error_code_value[0] = '\\0';
            javan_last_error_summary_value[0] = '\\0';
            javan_last_error_class_value[0] = '\\0';
            javan_last_error_method_value[0] = '\\0';
            javan_last_error_file_value[0] = '\\0';
            javan_last_error_source_line_value[0] = '\\0';
            javan_last_error_why_value[0] = '\\0';
            javan_last_error_fix_value[0] = '\\0';
            javan_last_error_detail_value[0] = '\\0';
            javan_last_error_line_value = -1;
            javan_last_error_bytecode_offset_value = -1;
        }

        static void javan_record_error(const char* value) {
            const char* source = value == NULL ? "javan panic" : value;
            javan_clear_error_fields();
            unsigned long length = strlen(source);
            if (length >= sizeof(javan_last_error_value)) {
                length = sizeof(javan_last_error_value) - 1;
            }
            memcpy(javan_last_error_value, source, length);
            javan_last_error_value[length] = '\\0';
            javan_copy_error_field(javan_last_error_code_value, sizeof(javan_last_error_code_value), "JAVAN-RUNTIME-PANIC");
            javan_copy_error_field(javan_last_error_summary_value, sizeof(javan_last_error_summary_value), "native runtime panic");
            javan_copy_error_field(javan_last_error_detail_value, sizeof(javan_last_error_detail_value), source);
            javan_last_error_line_value = -1;
            javan_last_error_bytecode_offset_value = -1;
            javan_last_error_set = 1;
        }

        static const char* javan_safe_text(const char* value, const char* fallback) {
            if (value == NULL || value[0] == '\\0') {
                return fallback;
            }
            return value;
        }

        static int javan_first_code_column(const char* value) {
            if (value == NULL) {
                return 0;
            }
            int index = 0;
            while (value[index] == ' ' || value[index] == '\\t') {
                index++;
            }
            return index;
        }

        static void javan_print_source_code(const char* value) {
            if (value == NULL || value[0] == '\\0') {
                return;
            }
            fprintf(stderr, "Code:\\n");
            fprintf(stderr, "  %s\\n  ", value);
            int column = javan_first_code_column(value);
            for (int index = 0; index < column; index++) {
                fputc(value[index] == '\\t' ? '\\t' : ' ', stderr);
            }
            fprintf(stderr, "^ here\\n\\n");
        }

        static void javan_record_error_at(
            const char* code,
            const char* summary,
            const char* class_name,
            const char* method,
            const char* file,
            int line,
            int bytecode_offset,
            const char* source_line,
            const char* why,
            const char* fix,
            const char* detail
        ) {
            const char* safe_code = javan_safe_text(code, "JAVAN-RUNTIME-PANIC");
            const char* safe_summary = javan_safe_text(summary, "native runtime panic");
            const char* safe_class = javan_safe_text(class_name, "unknown");
            const char* safe_method = javan_safe_text(method, "unknown");
            const char* safe_file = javan_safe_text(file, "unknown source");
            const char* safe_detail = javan_safe_text(detail, "javan panic");
            javan_clear_error_fields();
            javan_copy_error_field(javan_last_error_code_value, sizeof(javan_last_error_code_value), safe_code);
            javan_copy_error_field(javan_last_error_summary_value, sizeof(javan_last_error_summary_value), safe_summary);
            javan_copy_error_field(javan_last_error_class_value, sizeof(javan_last_error_class_value), safe_class);
            javan_copy_error_field(javan_last_error_method_value, sizeof(javan_last_error_method_value), safe_method);
            javan_copy_error_field(javan_last_error_file_value, sizeof(javan_last_error_file_value), safe_file);
            javan_copy_error_field(javan_last_error_source_line_value, sizeof(javan_last_error_source_line_value), source_line);
            javan_copy_error_field(javan_last_error_why_value, sizeof(javan_last_error_why_value), why);
            javan_copy_error_field(javan_last_error_fix_value, sizeof(javan_last_error_fix_value), fix);
            javan_copy_error_field(javan_last_error_detail_value, sizeof(javan_last_error_detail_value), safe_detail);
            javan_last_error_line_value = line;
            javan_last_error_bytecode_offset_value = bytecode_offset;
            if (line >= 0) {
                snprintf(
                    javan_last_error_value,
                    sizeof(javan_last_error_value),
                    "[%s] %s at %s.%s(%s:%d) bytecode:%d detail:%s",
                    safe_code,
                    safe_summary,
                    safe_class,
                    safe_method,
                    safe_file,
                    line,
                    bytecode_offset,
                    safe_detail
                );
            } else {
                snprintf(
                    javan_last_error_value,
                    sizeof(javan_last_error_value),
                    "[%s] %s at %s.%s(%s) bytecode:%d detail:%s",
                    safe_code,
                    safe_summary,
                    safe_class,
                    safe_method,
                    safe_file,
                    bytecode_offset,
                    safe_detail
                );
            }
            javan_last_error_value[sizeof(javan_last_error_value) - 1] = '\\0';
            javan_last_error_set = 1;
        }

        const char* javan_last_error(void) {
            return javan_last_error_set == 0 ? NULL : javan_last_error_value;
        }

        const char* javan_last_error_code(void) {
            return javan_last_error_field(javan_last_error_code_value);
        }

        const char* javan_last_error_summary(void) {
            return javan_last_error_field(javan_last_error_summary_value);
        }

        const char* javan_last_error_class(void) {
            return javan_last_error_field(javan_last_error_class_value);
        }

        const char* javan_last_error_method(void) {
            return javan_last_error_field(javan_last_error_method_value);
        }

        const char* javan_last_error_file(void) {
            return javan_last_error_field(javan_last_error_file_value);
        }

        int javan_last_error_line(void) {
            return javan_last_error_set == 0 ? -1 : javan_last_error_line_value;
        }

        int javan_last_error_bytecode_offset(void) {
            return javan_last_error_set == 0 ? -1 : javan_last_error_bytecode_offset_value;
        }

        const char* javan_last_error_source_line(void) {
            return javan_last_error_field(javan_last_error_source_line_value);
        }

        const char* javan_last_error_why(void) {
            return javan_last_error_field(javan_last_error_why_value);
        }

        const char* javan_last_error_fix(void) {
            return javan_last_error_field(javan_last_error_fix_value);
        }

        const char* javan_last_error_detail(void) {
            return javan_last_error_field(javan_last_error_detail_value);
        }

        void javan_clear_error(void) {
            javan_clear_error_fields();
            javan_last_error_set = 0;
        }

        static char* javan_result_copy_text(const char* value) {
            if (value == NULL) {
                return NULL;
            }
            unsigned long length = strlen(value);
            char* result = (char*) malloc(length + 1);
            if (result == NULL) {
                javan_panic("out of memory");
            }
            memcpy(result, value, length);
            result[length] = '\\0';
            return result;
        }

        static JavanResult javan_result_empty(int ok) {
            JavanResult result;
            result.ok = ok;
            result.code = NULL;
            result.message = NULL;
            result.summary = NULL;
            result.class_name = NULL;
            result.method = NULL;
            result.file = NULL;
            result.line = -1;
            result.bytecode_offset = -1;
            result.source_line = NULL;
            result.why = NULL;
            result.fix = NULL;
            result.detail = NULL;
            return result;
        }

        JavanResult javan_result_ok(void) {
            return javan_result_empty(1);
        }

        JavanResult javan_result_error_from_last_error(void) {
            JavanResult result = javan_result_empty(0);
            result.code = javan_result_copy_text(javan_last_error_code());
            result.message = javan_result_copy_text(javan_last_error());
            result.summary = javan_result_copy_text(javan_last_error_summary());
            result.class_name = javan_result_copy_text(javan_last_error_class());
            result.method = javan_result_copy_text(javan_last_error_method());
            result.file = javan_result_copy_text(javan_last_error_file());
            result.line = javan_last_error_line();
            result.bytecode_offset = javan_last_error_bytecode_offset();
            result.source_line = javan_result_copy_text(javan_last_error_source_line());
            result.why = javan_result_copy_text(javan_last_error_why());
            result.fix = javan_result_copy_text(javan_last_error_fix());
            result.detail = javan_result_copy_text(javan_last_error_detail());
            return result;
        }

        JavanResult javan_result_error_message(const char* code, const char* summary, const char* detail) {
            const char* safe_code = javan_safe_text(code, "JAVAN-ABI-ERROR");
            const char* safe_summary = javan_safe_text(summary, "invalid native ABI call");
            const char* safe_detail = javan_safe_text(detail, "invalid native ABI call");
            JavanResult result = javan_result_empty(0);
            result.code = javan_result_copy_text(safe_code);
            result.message = javan_result_copy_text(safe_detail);
            result.summary = javan_result_copy_text(safe_summary);
            result.detail = javan_result_copy_text(safe_detail);
            return result;
        }

        void javan_result_free(JavanResult* result) {
            if (result == NULL) {
                return;
            }
            free(result->code);
            free(result->message);
            free(result->summary);
            free(result->class_name);
            free(result->method);
            free(result->file);
            free(result->source_line);
            free(result->why);
            free(result->fix);
            free(result->detail);
            *result = javan_result_empty(0);
        }

        void javan_panic_set_target(jmp_buf* target) {
            javan_panic_target = target;
            javan_clear_error();
        }

        void javan_panic_clear_target(jmp_buf* target) {
            if (javan_panic_target == target) {
                javan_panic_target = NULL;
            }
        }

        void javan_source_enter(
            JavanSourceContext* context,
            const char* code,
            const char* summary,
            const char* class_name,
            const char* method,
            const char* file,
            int line,
            int bytecode_offset,
            const char* source_line,
            const char* why,
            const char* fix
        ) {
            if (context == NULL) {
                return;
            }
            context->code = code;
            context->summary = summary;
            context->class_name = class_name;
            context->method = method;
            context->file = file;
            context->line = line;
            context->bytecode_offset = bytecode_offset;
            context->source_line = source_line;
            context->why = why;
            context->fix = fix;
            context->previous = javan_source_context_top;
            javan_source_context_top = context;
        }

        void javan_source_clear(JavanSourceContext* context) {
            if (context == NULL) {
                return;
            }
            if (javan_source_context_top == context) {
                javan_source_context_top = context->previous;
            } else {
                JavanSourceContext* cursor = javan_source_context_top;
                while (cursor != NULL && cursor->previous != context) {
                    cursor = cursor->previous;
                }
                if (cursor != NULL) {
                    cursor->previous = context->previous;
                }
            }
            context->code = NULL;
            context->summary = NULL;
            context->class_name = NULL;
            context->method = NULL;
            context->file = NULL;
            context->line = -1;
            context->bytecode_offset = -1;
            context->source_line = NULL;
            context->why = NULL;
            context->fix = NULL;
            context->previous = NULL;
        }

        void javan_println(const char* value) {
            puts(value == NULL ? "" : value);
            fflush(stdout);
        }

        void javan_print(const char* value) {
            fputs(value == NULL ? "" : value, stdout);
            fflush(stdout);
        }

        void javan_eprintln(const char* value) {
            fputs(value == NULL ? "" : value, stderr);
            fputc('\\n', stderr);
            fflush(stderr);
        }

        void javan_eprint(const char* value) {
            fputs(value == NULL ? "" : value, stderr);
            fflush(stderr);
        }

        static char javan_system_out_sentinel;
        static char javan_system_err_sentinel;

        void* javan_system_out(void) {
            return &javan_system_out_sentinel;
        }

        void* javan_system_err(void) {
            return &javan_system_err_sentinel;
        }

        static int javan_printstream_is_err(void* stream) {
            return stream == &javan_system_err_sentinel;
        }

        void javan_println_int(int value) {
            printf("%d\\n", value);
        }

        void javan_eprintln_int(int value) {
            fprintf(stderr, "%d\\n", value);
            fflush(stderr);
        }

        void javan_println_long(long long value) {
            printf("%lld\\n", value);
        }

        void javan_eprintln_long(long long value) {
            fprintf(stderr, "%lld\\n", value);
            fflush(stderr);
        }

        static void javan_format_real(char* buffer, unsigned long size, double value, const char* format) {
            if (isnan(value)) {
                snprintf(buffer, size, "NaN");
                return;
            }
            if (isinf(value)) {
                snprintf(buffer, size, value < 0.0 ? "-Infinity" : "Infinity");
                return;
            }
            snprintf(buffer, size, format, value);
            if (strchr(buffer, '.') == NULL && strchr(buffer, 'e') == NULL && strchr(buffer, 'E') == NULL) {
                unsigned long length = strlen(buffer);
                if (length + 2 < size) {
                    buffer[length] = '.';
                    buffer[length + 1] = '0';
                    buffer[length + 2] = '\\0';
                }
            }
        }

        void javan_println_float(float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            puts(buffer);
        }

        void javan_eprintln_float(float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            fputs(buffer, stderr);
            fputc('\\n', stderr);
            fflush(stderr);
        }

        void javan_println_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            puts(buffer);
        }

        void javan_eprintln_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            fputs(buffer, stderr);
            fputc('\\n', stderr);
            fflush(stderr);
        }

        void javan_println_bool(int value) {
            puts(value == 0 ? "false" : "true");
        }

        void javan_eprintln_bool(int value) {
            fputs(value == 0 ? "false" : "true", stderr);
            fputc('\\n', stderr);
            fflush(stderr);
        }

        void javan_printstream_print(void* stream, const char* value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprint(value);
                return;
            }
            javan_print(value);
        }

        void javan_printstream_println(void* stream, const char* value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln(value);
                return;
            }
            javan_println(value);
        }

        void javan_printstream_println_int(void* stream, int value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln_int(value);
                return;
            }
            javan_println_int(value);
        }

        void javan_printstream_println_long(void* stream, long long value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln_long(value);
                return;
            }
            javan_println_long(value);
        }

        void javan_printstream_println_float(void* stream, float value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln_float(value);
                return;
            }
            javan_println_float(value);
        }

        void javan_printstream_println_double(void* stream, double value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln_double(value);
                return;
            }
            javan_println_double(value);
        }

        void javan_printstream_println_bool(void* stream, int value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln_bool(value);
                return;
            }
            javan_println_bool(value);
        }

        int javan_math_abs_int(int value) {
            if (value == INT_MIN) {
                return value;
            }
            return value < 0 ? -value : value;
        }

        long long javan_math_abs_long(long long value) {
            if (value == LLONG_MIN) {
                return value;
            }
            return value < 0 ? -value : value;
        }

        int javan_math_min_int(int left, int right) {
            return left <= right ? left : right;
        }

        long long javan_math_min_long(long long left, long long right) {
            return left <= right ? left : right;
        }

        int javan_math_max_int(int left, int right) {
            return left >= right ? left : right;
        }

        long long javan_math_max_long(long long left, long long right) {
            return left >= right ? left : right;
        }

        int javan_math_to_int_exact(long long value) {
            if (value < INT_MIN || value > INT_MAX) {
                javan_panic("integer overflow");
            }
            return (int) value;
        }

        int javan_int_shl(int value, int shift) {
            return (int) (((unsigned int) value) << (shift & 31));
        }

        long long javan_long_shl(long long value, int shift) {
            return (long long) (((unsigned long long) value) << (shift & 63));
        }

        int javan_int_shr(int value, int shift) {
            return value >> (shift & 31);
        }

        long long javan_long_shr(long long value, int shift) {
            return value >> (shift & 63);
        }

        int javan_int_ushr(int value, int shift) {
            return (int) (((unsigned int) value) >> (shift & 31));
        }

        long long javan_long_ushr(long long value, int shift) {
            return (long long) (((unsigned long long) value) >> (shift & 63));
        }

        long long javan_i2l(int value) {
            return (long long) value;
        }

        float javan_i2f(int value) {
            return (float) value;
        }

        double javan_i2d(int value) {
            return (double) value;
        }

        int javan_l2i(long long value) {
            return (int) value;
        }

        int javan_i2b(int value) {
            return (int) ((signed char) value);
        }

        int javan_i2s(int value) {
            return (int) ((short) value);
        }

        long long javan_system_current_time_millis(void) {
            struct timeval now;
            if (gettimeofday(&now, NULL) != 0) {
                javan_panic("currentTimeMillis failed");
            }
            return ((long long) now.tv_sec * 1000LL) + ((long long) now.tv_usec / 1000LL);
        }

        long long javan_system_nano_time(void) {
        #if defined(CLOCK_MONOTONIC)
            struct timespec now;
            if (clock_gettime(CLOCK_MONOTONIC, &now) == 0) {
                return ((long long) now.tv_sec * 1000000000LL) + (long long) now.tv_nsec;
            }
        #endif
            return javan_system_current_time_millis() * 1000000LL;
        }

        void* javan_system_line_separator(void) {
        #if defined(_WIN32)
            return "\\r\\n";
        #else
            return "\\n";
        #endif
        }

        int javan_file_separator_char(void) {
        #if defined(_WIN32)
            return '\\\\';
        #else
            return '/';
        #endif
        }

        int javan_file_path_separator_char(void) {
        #if defined(_WIN32)
            return ';';
        #else
            return ':';
        #endif
        }

        void* javan_file_path_separator(void) {
        #if defined(_WIN32)
            return ";";
        #else
            return ":";
        #endif
        }

        void* javan_system_getenv(const char* name) {
            if (name == NULL) {
                javan_panic("environment variable name is null");
            }
            return getenv(name);
        }

        void* javan_system_get_property(const char* key) {
            if (key == NULL) {
                javan_panic("system property name is null");
            }
            if (strcmp(key, "os.name") == 0) {
        #if defined(_WIN32)
                return "Windows";
        #elif defined(__APPLE__)
                return "Mac OS X";
        #elif defined(__linux__)
                return "Linux";
        #else
                return "Unknown";
        #endif
            }
            if (strcmp(key, "os.arch") == 0) {
        #if defined(__aarch64__) || defined(_M_ARM64)
                return "aarch64";
        #elif defined(__x86_64__) || defined(_M_X64)
                return "x86_64";
        #elif defined(__i386__) || defined(_M_IX86)
                return "x86";
        #elif defined(__arm__) || defined(_M_ARM)
                return "arm";
        #else
                return "unknown";
        #endif
            }
            if (strcmp(key, "user.dir") == 0) {
                char* buffer = javan_string_alloc(4096);
                if (getcwd(buffer, 4096) == NULL) {
                    javan_free(buffer);
                    return NULL;
                }
                return buffer;
            }
            if (strcmp(key, "user.home") == 0) {
        #if defined(_WIN32)
                return getenv("USERPROFILE");
        #else
                return getenv("HOME");
        #endif
            }
            if (strcmp(key, "java.home") == 0) {
                return getenv("JAVA_HOME");
            }
            if (strcmp(key, "java.version") == 0) {
                return "native";
            }
            return NULL;
        }

        void* javan_system_get_property_or_default(const char* key, const char* fallback) {
            void* value = javan_system_get_property(key);
            return value == NULL ? (void*) fallback : value;
        }

        void* javan_objects_require_non_null(void* value) {
            if (value == NULL) {
                javan_panic("null object");
            }
            return value;
        }

        void* javan_objects_require_non_null_msg(void* value, const char* message) {
            if (value == NULL) {
                javan_panic(message == NULL ? "null object" : message);
            }
            return value;
        }
        """;

    private static final String SOURCE_HEAP = """
        typedef struct javan_allocation_node {
            void* value;
            void* base;
            unsigned long size;
            int kind;
            int type_id;
            int collectible;
            int runtime_kind;
            unsigned int mark;
            struct javan_allocation_node* next;
        } javan_allocation_node;

        typedef struct {
            unsigned long magic;
            unsigned long size;
        } javan_export_header;

        #define JAVAN_EXPORT_ALLOCATION_MAGIC 0x4a4156414e454650UL
        #define JAVAN_HEAP_KIND_RUNTIME 1
        #define JAVAN_HEAP_KIND_OBJECT 2
        #define JAVAN_HEAP_KIND_ARRAY 3
        #define JAVAN_HEAP_KIND_EXPORT 4
        #define JAVAN_ARRAY_KIND_OBJECT 1
        #define JAVAN_ARRAY_KIND_INT 2
        #define JAVAN_ARRAY_KIND_LONG 3
        #define JAVAN_ARRAY_KIND_FLOAT 4
        #define JAVAN_ARRAY_KIND_DOUBLE 5
        #define JAVAN_ARRAY_KIND_BYTE 6
        #define JAVAN_ARRAY_KIND_BOOLEAN 7
        #define JAVAN_ARRAY_KIND_SHORT 8
        #define JAVAN_ARRAY_KIND_CHAR 9
        #define JAVAN_RUNTIME_KIND_NONE 0
        #define JAVAN_RUNTIME_KIND_OBJECT_LIST 1
        #define JAVAN_RUNTIME_KIND_OBJECT_ITERATOR 2
        #define JAVAN_RUNTIME_KIND_OBJECT_MAP 3
        #define JAVAN_RUNTIME_KIND_OPTIONAL 4
        #define JAVAN_RUNTIME_KIND_STRING 5
        #define JAVAN_RUNTIME_KIND_PROCESS_RESULT 6
        #define JAVAN_RUNTIME_KIND_STRING_BUILDER 7
        #define JAVAN_RUNTIME_KIND_OWNED_BUFFER 8
        #define JAVAN_RUNTIME_KIND_INET_ADDRESS 9
        #define JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS 10
        #define JAVAN_RUNTIME_KIND_SOCKET 11
        #define JAVAN_RUNTIME_KIND_SERVER_SOCKET 12
        #define JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM 13
        #define JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM 14
        #define JAVAN_RUNTIME_KIND_URI 15
        #define JAVAN_RUNTIME_KIND_HTTP_CLIENT 16
        #define JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER 17
        #define JAVAN_RUNTIME_KIND_HTTP_REQUEST 18
        #define JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER 19
        #define JAVAN_RUNTIME_KIND_HTTP_RESPONSE 20
        #define JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER 21

        typedef struct {
            int magic;
            int length;
            int capacity;
            int immutable;
            int mod_count;
            void** values;
        } javan_object_list;

        typedef struct {
            int magic;
            int index;
            int expected_mod_count;
            int reserved;
            javan_object_list* list;
        } javan_object_iterator;

        typedef struct {
            int magic;
            int length;
            int capacity;
            int immutable;
            int mod_count;
            int reserved0;
            int reserved1;
            int reserved2;
            void** keys;
            void** values;
        } javan_object_map;

        typedef struct {
            int magic;
            int length;
            int capacity;
            int reserved;
            char* values;
        } javan_string_builder;

        typedef struct {
            int magic;
            int present;
            int reserved0;
            int reserved1;
            void* value;
        } javan_optional;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            char* host_address;
            char* host_name;
        } javan_inet_address;

        typedef struct {
            int magic;
            int port;
            int reserved0;
            int reserved1;
            javan_inet_address* address;
        } javan_inet_socket_address;

        typedef struct {
            int magic;
            int fd;
            int connected;
            int closed;
            int local_port;
            int remote_port;
            javan_inet_address* local_address;
            javan_inet_address* remote_address;
        } javan_socket;

        typedef struct {
            int magic;
            int fd;
            int closed;
            int local_port;
            int reserved0;
            int reserved1;
            javan_inet_address* local_address;
        } javan_server_socket;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            javan_socket* socket;
        } javan_socket_input_stream_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            javan_socket* socket;
        } javan_socket_output_stream_value;

        typedef struct {
            int magic;
            int port;
            int reserved0;
            int reserved1;
            char* scheme;
            char* host;
            char* target;
        } javan_uri_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
        } javan_http_client_value;

        typedef struct {
            int magic;
            int method;
            int reserved0;
            int reserved1;
            javan_uri_value* uri;
            javan_object_list* headers;
            void* body;
        } javan_http_request_builder_value;

        typedef struct {
            int magic;
            int method;
            int reserved0;
            int reserved1;
            javan_uri_value* uri;
            javan_object_list* headers;
            void* body;
        } javan_http_request_value;

        typedef struct {
            int magic;
            int kind;
            int reserved0;
            int reserved1;
            void* value;
        } javan_http_body_publisher_value;

        typedef struct {
            int magic;
            int kind;
            int reserved0;
            int reserved1;
        } javan_http_body_handler_value;

        typedef struct {
            int magic;
            int status_code;
            int reserved0;
            int reserved1;
            void* body;
        } javan_http_response_value;

        #define JAVAN_OBJECT_LIST_MAGIC 0x4a4c5354
        #define JAVAN_OBJECT_ITERATOR_MAGIC 0x4a495452
        #define JAVAN_OBJECT_MAP_MAGIC 0x4a4d4150
        #define JAVAN_STRING_BUILDER_MAGIC 0x4a53424c
        #define JAVAN_OPTIONAL_MAGIC 0x4a4f5054
        #define JAVAN_INET_ADDRESS_MAGIC 0x4a494144
        #define JAVAN_INET_SOCKET_ADDRESS_MAGIC 0x4a495341
        #define JAVAN_SOCKET_MAGIC 0x4a534f43
        #define JAVAN_SERVER_SOCKET_MAGIC 0x4a535352
        #define JAVAN_SOCKET_INPUT_STREAM_MAGIC 0x4a534953
        #define JAVAN_SOCKET_OUTPUT_STREAM_MAGIC 0x4a534f53
        #define JAVAN_URI_MAGIC 0x4a555249
        #define JAVAN_HTTP_CLIENT_MAGIC 0x4a485443
        #define JAVAN_HTTP_REQUEST_BUILDER_MAGIC 0x4a485442
        #define JAVAN_HTTP_REQUEST_MAGIC 0x4a485452
        #define JAVAN_HTTP_BODY_PUBLISHER_MAGIC 0x4a485450
        #define JAVAN_HTTP_BODY_HANDLER_MAGIC 0x4a485448
        #define JAVAN_HTTP_RESPONSE_MAGIC 0x4a485453
        #define JAVAN_HTTP_METHOD_GET 1
        #define JAVAN_HTTP_METHOD_POST 2
        #define JAVAN_HTTP_METHOD_PUT 3
        #define JAVAN_HTTP_BODY_KIND_STRING 1
        #define JAVAN_HTTP_BODY_KIND_BYTE_ARRAY 2

        typedef struct javan_process_result {
            int exit_code;
            char* stdout_value;
            char* stderr_value;
        } javan_process_result;

        typedef struct javan_root_frame {
            void*** roots;
            int count;
            struct javan_root_frame* next;
        } javan_root_frame;

        typedef void (*javan_native_resource_cleanup)(void* value);

        typedef struct javan_native_resource_frame {
            void* resource;
            javan_native_resource_cleanup cleanup;
            struct javan_native_resource_frame* next;
        } javan_native_resource_frame;

        static javan_allocation_node* javan_allocations = NULL;
        static int javan_allocator_cleanup_registered = 0;
        static int javan_allocator_cleaning = 0;
        static unsigned long javan_total_allocations_value = 0;
        static unsigned long javan_live_allocations_value = 0;
        static unsigned long javan_total_allocated_bytes_value = 0;
        static unsigned long javan_live_allocated_bytes_value = 0;
        static unsigned long javan_peak_live_allocated_bytes_value = 0;
        static JavanTypeDescriptor* javan_type_descriptors_value = NULL;
        static int javan_type_descriptor_count_value = 0;
        static void*** javan_static_roots_value = NULL;
        static int javan_static_root_count_value = 0;
        static javan_root_frame* javan_root_frames_value = NULL;
        static javan_native_resource_frame* javan_native_resource_frames_value = NULL;
        static int javan_root_frame_depth_value = 0;
        static int javan_frame_root_count_value = 0;
        static int javan_heap_stress_initialized = 0;
        static unsigned long javan_heap_stress_interval = 0;
        static unsigned long javan_heap_stress_ticks = 0;
        static int javan_allocation_limit_initialized = 0;
        static unsigned long javan_max_allocation_bytes = 0;
        static unsigned long javan_heap_limit_bytes = 0;
        static int javan_gc_enabled_value = 0;
        static int javan_gc_collecting = 0;
        static int javan_gc_safe_point_initialized = 0;
        static unsigned long javan_gc_safe_point_interval = 0;
        static unsigned long javan_gc_safe_point_ticks = 0;
        static unsigned long javan_gc_collection_count_value = 0;
        static unsigned long javan_gc_collected_allocations_value = 0;
        static unsigned long javan_gc_collected_bytes_value = 0;
        static void* javan_current_thread_value = NULL;

        static void javan_account_allocation(unsigned long size) {
            javan_total_allocations_value++;
            javan_live_allocations_value++;
            javan_total_allocated_bytes_value += size;
            javan_live_allocated_bytes_value += size;
            if (javan_live_allocated_bytes_value > javan_peak_live_allocated_bytes_value) {
                javan_peak_live_allocated_bytes_value = javan_live_allocated_bytes_value;
            }
        }

        static void javan_account_free(unsigned long size) {
            if (javan_live_allocations_value == 0 || javan_live_allocated_bytes_value < size) {
                javan_panic("heap accounting underflow");
            }
            javan_live_allocations_value--;
            javan_live_allocated_bytes_value -= size;
        }

        static void javan_account_realloc(unsigned long old_size, unsigned long new_size) {
            if (javan_live_allocated_bytes_value < old_size) {
                javan_panic("heap accounting underflow");
            }
            javan_live_allocated_bytes_value = javan_live_allocated_bytes_value - old_size + new_size;
            if (new_size > old_size) {
                javan_total_allocated_bytes_value += new_size - old_size;
            }
            if (javan_live_allocated_bytes_value > javan_peak_live_allocated_bytes_value) {
                javan_peak_live_allocated_bytes_value = javan_live_allocated_bytes_value;
            }
        }

        static void javan_heap_maybe_validate(void);
        static void javan_object_registry_cleanup(void);

        static void javan_native_file_cleanup(void* value) {
            if (value != NULL) {
                (void) fclose((FILE*) value);
            }
        }

        static void javan_native_dir_cleanup(void* value) {
            if (value != NULL) {
                (void) closedir((DIR*) value);
            }
        }

        static void javan_native_resource_push(
            javan_native_resource_frame* frame,
            void* resource,
            javan_native_resource_cleanup cleanup
        ) {
            if (frame == NULL || resource == NULL || cleanup == NULL) {
                javan_panic("invalid native resource frame");
            }
            frame->resource = resource;
            frame->cleanup = cleanup;
            frame->next = javan_native_resource_frames_value;
            javan_native_resource_frames_value = frame;
        }

        static void javan_native_resource_pop(javan_native_resource_frame* frame) {
            if (frame == NULL || javan_native_resource_frames_value != frame) {
                javan_panic("native resource frame mismatch");
            }
            javan_native_resource_frames_value = frame->next;
            frame->resource = NULL;
            frame->cleanup = NULL;
            frame->next = NULL;
        }

        static void javan_native_resource_cleanup_all(void) {
            javan_native_resource_frame* frame = javan_native_resource_frames_value;
            javan_native_resource_frames_value = NULL;
            while (frame != NULL) {
                javan_native_resource_frame* next = frame->next;
                void* resource = frame->resource;
                javan_native_resource_cleanup cleanup = frame->cleanup;
                frame->resource = NULL;
                frame->cleanup = NULL;
                frame->next = NULL;
                if (resource != NULL && cleanup != NULL) {
                    cleanup(resource);
                }
                frame = next;
            }
        }

        static void javan_root_frame_cleanup(void) {
            javan_root_frame* frame = javan_root_frames_value;
            while (frame != NULL) {
                javan_root_frame* next = frame->next;
                free(frame);
                frame = next;
            }
            javan_root_frames_value = NULL;
            javan_root_frame_depth_value = 0;
            javan_frame_root_count_value = 0;
        }

        static void javan_allocator_cleanup(void) {
            javan_allocator_cleaning = 1;
            javan_native_resource_cleanup_all();
            javan_root_frame_cleanup();
            javan_object_registry_cleanup();
            javan_allocation_node* node = javan_allocations;
            javan_allocations = NULL;
            while (node != NULL) {
                javan_allocation_node* next = node->next;
                free(node->base);
                free(node);
                node = next;
            }
            javan_live_allocations_value = 0;
            javan_live_allocated_bytes_value = 0;
            javan_allocator_cleaning = 0;
        }

        static void javan_allocator_ensure_cleanup(void) {
            if (javan_allocator_cleanup_registered == 0) {
                if (atexit(javan_allocator_cleanup) != 0) {
                    javan_panic("unable to register allocator cleanup");
                }
                javan_allocator_cleanup_registered = 1;
            }
        }

        static void javan_track_allocation(void* value, void* base, unsigned long size, int kind, int type_id) {
            javan_allocator_ensure_cleanup();
            javan_allocation_node* node = (javan_allocation_node*) malloc(sizeof(javan_allocation_node));
            if (node == NULL) {
                javan_gc_collect();
                node = (javan_allocation_node*) malloc(sizeof(javan_allocation_node));
                if (node == NULL) {
                    free(base);
                    javan_panic("out of memory");
                }
            }
            node->value = value;
            node->base = base;
            node->size = size;
            node->kind = kind;
            node->type_id = type_id;
            node->collectible = 0;
            node->runtime_kind = JAVAN_RUNTIME_KIND_NONE;
            node->mark = 0;
            node->next = javan_allocations;
            javan_allocations = node;
            javan_account_allocation(size);
            javan_heap_maybe_validate();
        }

        static javan_allocation_node* javan_find_allocation(void* value, javan_allocation_node** previous) {
            javan_allocation_node* prior = NULL;
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                if (node->value == value) {
                    if (previous != NULL) {
                        *previous = prior;
                    }
                    return node;
                }
                prior = node;
                node = node->next;
            }
            if (previous != NULL) {
                *previous = NULL;
            }
            return NULL;
        }

        void javan_register_type_descriptors(JavanTypeDescriptor* descriptors, int count) {
            if (count < 0) {
                javan_panic("invalid type descriptor count");
            }
            if (count > 0 && descriptors == NULL) {
                javan_panic("invalid type descriptor inventory");
            }
            for (int index = 0; index < count; index++) {
                if (descriptors[index].type_id == 0 || descriptors[index].name == NULL || descriptors[index].object_field_count < 0) {
                    javan_panic("invalid type descriptor");
                }
                if (descriptors[index].object_field_count > 0 && descriptors[index].object_field_offsets == NULL) {
                    javan_panic("invalid type field descriptor");
                }
            }
            javan_type_descriptors_value = descriptors;
            javan_type_descriptor_count_value = count;
            javan_heap_maybe_validate();
        }

        #define JAVAN_TYPE_JAVA_LANG_INTEGER -1001
        #define JAVAN_TYPE_JAVA_LANG_LONG -1002
        #define JAVAN_TYPE_JAVA_LANG_FLOAT -1003
        #define JAVAN_TYPE_JAVA_LANG_DOUBLE -1004
        #define JAVAN_TYPE_JAVA_LANG_BOOLEAN -1005
        #define JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME -1006
        #define JAVAN_TYPE_JAVA_TIME_DURATION -1007
        #define JAVAN_TYPE_JAVA_LANG_THREAD -1008

        static int javan_array_kind_collectible(int type_id) {
            return type_id == JAVAN_ARRAY_KIND_OBJECT
                || type_id == JAVAN_ARRAY_KIND_INT
                || type_id == JAVAN_ARRAY_KIND_LONG
                || type_id == JAVAN_ARRAY_KIND_FLOAT
                || type_id == JAVAN_ARRAY_KIND_DOUBLE
                || type_id == JAVAN_ARRAY_KIND_BYTE
                || type_id == JAVAN_ARRAY_KIND_BOOLEAN
                || type_id == JAVAN_ARRAY_KIND_SHORT
                || type_id == JAVAN_ARRAY_KIND_CHAR;
        }

        static int javan_object_kind_collectible(int type_id) {
            return type_id > 0
                || type_id == JAVAN_TYPE_JAVA_LANG_INTEGER
                || type_id == JAVAN_TYPE_JAVA_LANG_LONG
                || type_id == JAVAN_TYPE_JAVA_LANG_FLOAT
                || type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE
                || type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN
                || type_id == JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME
                || type_id == JAVAN_TYPE_JAVA_TIME_DURATION
                || type_id == JAVAN_TYPE_JAVA_LANG_THREAD;
        }

        static void javan_update_allocation_metadata(void* value, int kind, int type_id) {
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_panic("unknown runtime allocation");
            }
            node->kind = kind;
            node->type_id = type_id;
            node->collectible = ((kind == JAVAN_HEAP_KIND_OBJECT && javan_object_kind_collectible(type_id) != 0)
                || (kind == JAVAN_HEAP_KIND_ARRAY && javan_array_kind_collectible(type_id) != 0)) ? 1 : 0;
            javan_heap_maybe_validate();
        }

        static void javan_update_runtime_allocation_kind(void* value, int runtime_kind) {
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_panic("unknown runtime allocation");
            }
            if (node->kind != JAVAN_HEAP_KIND_RUNTIME) {
                javan_panic("invalid runtime allocation tag");
            }
            node->runtime_kind = runtime_kind;
            node->collectible = runtime_kind == JAVAN_RUNTIME_KIND_STRING
                || runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_RESULT
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_ITERATOR
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP
                || runtime_kind == JAVAN_RUNTIME_KIND_OPTIONAL
                || runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_OWNED_BUFFER
                || runtime_kind == JAVAN_RUNTIME_KIND_INET_ADDRESS
                || runtime_kind == JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS
                || runtime_kind == JAVAN_RUNTIME_KIND_SOCKET
                || runtime_kind == JAVAN_RUNTIME_KIND_SERVER_SOCKET
                || runtime_kind == JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM
                || runtime_kind == JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM
                || runtime_kind == JAVAN_RUNTIME_KIND_URI
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CLIENT
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_RESPONSE;
            javan_heap_maybe_validate();
        }

        void javan_root_frame_push(void*** roots, int count) {
            if (count < 0) {
                javan_panic("invalid root frame count");
            }
            if (count > 0 && roots == NULL) {
                javan_panic("invalid root frame");
            }
            for (int index = 0; index < count; index++) {
                if (roots[index] == NULL) {
                    javan_panic("invalid root frame slot");
                }
            }
            javan_allocator_ensure_cleanup();
            javan_root_frame* frame = (javan_root_frame*) malloc(sizeof(javan_root_frame));
            if (frame == NULL) {
                javan_panic("out of memory");
            }
            frame->roots = roots;
            frame->count = count;
            frame->next = javan_root_frames_value;
            javan_root_frames_value = frame;
            javan_root_frame_depth_value++;
            javan_frame_root_count_value += count;
            javan_heap_maybe_validate();
        }

        void javan_root_frame_pop(void*** roots) {
            javan_root_frame* frame = javan_root_frames_value;
            if (frame == NULL) {
                javan_panic("root frame underflow");
            }
            if (frame->roots != roots) {
                javan_panic("root frame pop mismatch");
            }
            javan_root_frames_value = frame->next;
            javan_root_frame_depth_value--;
            javan_frame_root_count_value -= frame->count;
            free(frame);
            javan_heap_maybe_validate();
        }

        void javan_register_static_roots(void*** roots, int count) {
            if (count < 0) {
                javan_panic("invalid static root count");
            }
            if (count > 0 && roots == NULL) {
                javan_panic("invalid static root inventory");
            }
            javan_static_roots_value = roots;
            javan_static_root_count_value = count;
            javan_gc_enabled_value = 1;
            javan_heap_maybe_validate();
        }

        unsigned long javan_heap_live_allocations(void) {
            return javan_live_allocations_value;
        }

        unsigned long javan_heap_live_bytes(void) {
            return javan_live_allocated_bytes_value;
        }

        unsigned long javan_heap_total_allocations(void) {
            return javan_total_allocations_value;
        }

        unsigned long javan_heap_total_allocated_bytes(void) {
            return javan_total_allocated_bytes_value;
        }

        unsigned long javan_heap_peak_live_bytes(void) {
            return javan_peak_live_allocated_bytes_value;
        }

        unsigned long javan_heap_gc_collections(void) {
            return javan_gc_collection_count_value;
        }

        unsigned long javan_heap_gc_collected_allocations(void) {
            return javan_gc_collected_allocations_value;
        }

        unsigned long javan_heap_gc_collected_bytes(void) {
            return javan_gc_collected_bytes_value;
        }

        int javan_heap_type_descriptor_count(void) {
            return javan_type_descriptor_count_value;
        }

        int javan_heap_static_root_count(void) {
            return javan_static_root_count_value;
        }

        int javan_heap_root_frame_depth(void) {
            return javan_root_frame_depth_value;
        }

        int javan_heap_frame_root_count(void) {
            return javan_frame_root_count_value;
        }

        static void javan_validate_owned_runtime_buffer_reference(void* value) {
            if (value == NULL) {
                return;
            }
            javan_allocation_node* buffer = javan_find_allocation(value, NULL);
            if (buffer == NULL
                || buffer->kind != JAVAN_HEAP_KIND_RUNTIME
                || buffer->runtime_kind != JAVAN_RUNTIME_KIND_OWNED_BUFFER) {
                javan_panic("invalid runtime owned buffer reference");
            }
        }

        static void javan_validate_runtime_managed_reference(void* value) {
            if (value == NULL) {
                return;
            }
            if (javan_find_allocation(value, NULL) == NULL) {
                javan_panic("invalid runtime managed reference");
            }
        }

        static void javan_validate_runtime_container_references(javan_allocation_node* node) {
            if (node == NULL || node->kind != JAVAN_HEAP_KIND_RUNTIME || node->value == NULL) {
                return;
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST) {
                javan_object_list* list = (javan_object_list*) node->value;
                if (list->magic != JAVAN_OBJECT_LIST_MAGIC || list->length < 0 || list->capacity < 0 || list->length > list->capacity) {
                    javan_panic("invalid runtime list metadata");
                }
                javan_validate_owned_runtime_buffer_reference((void*) list->values);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP) {
                javan_object_map* map = (javan_object_map*) node->value;
                if (map->magic != JAVAN_OBJECT_MAP_MAGIC || map->length < 0 || map->capacity < 0 || map->length > map->capacity) {
                    javan_panic("invalid runtime map metadata");
                }
                javan_validate_owned_runtime_buffer_reference((void*) map->keys);
                javan_validate_owned_runtime_buffer_reference((void*) map->values);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                javan_string_builder* builder = (javan_string_builder*) node->value;
                if (builder->magic != JAVAN_STRING_BUILDER_MAGIC || builder->length < 0 || builder->capacity < 0 || builder->length > builder->capacity) {
                    javan_panic("invalid runtime string builder metadata");
                }
                if (builder->values != NULL && (builder->capacity <= 0 || builder->length >= builder->capacity)) {
                    javan_panic("invalid runtime string builder owned buffer");
                }
                javan_validate_owned_runtime_buffer_reference((void*) builder->values);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER) {
                javan_http_request_builder_value* builder = (javan_http_request_builder_value*) node->value;
                if (builder->magic != JAVAN_HTTP_REQUEST_BUILDER_MAGIC || builder->uri == NULL || builder->headers == NULL) {
                    javan_panic("invalid runtime http request builder metadata");
                }
                javan_validate_runtime_managed_reference((void*) builder->uri);
                javan_validate_runtime_managed_reference((void*) builder->headers);
                javan_validate_runtime_managed_reference(builder->body);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST) {
                javan_http_request_value* request = (javan_http_request_value*) node->value;
                if (request->magic != JAVAN_HTTP_REQUEST_MAGIC || request->uri == NULL || request->headers == NULL) {
                    javan_panic("invalid runtime http request metadata");
                }
                javan_validate_runtime_managed_reference((void*) request->uri);
                javan_validate_runtime_managed_reference((void*) request->headers);
                javan_validate_runtime_managed_reference(request->body);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER) {
                javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) node->value;
                if (publisher->magic != JAVAN_HTTP_BODY_PUBLISHER_MAGIC
                    || (publisher->kind != JAVAN_HTTP_BODY_KIND_STRING && publisher->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)
                    || publisher->value == NULL) {
                    javan_panic("invalid runtime http body publisher metadata");
                }
                javan_validate_runtime_managed_reference(publisher->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_INET_ADDRESS) {
                javan_inet_address* address = (javan_inet_address*) node->value;
                if (address->magic != JAVAN_INET_ADDRESS_MAGIC || address->host_address == NULL || address->host_name == NULL) {
                    javan_panic("invalid runtime inet address metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS) {
                javan_inet_socket_address* address = (javan_inet_socket_address*) node->value;
                if (address->magic != JAVAN_INET_SOCKET_ADDRESS_MAGIC || address->port < 0 || address->address == NULL) {
                    javan_panic("invalid runtime inet socket address metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SOCKET) {
                javan_socket* socket = (javan_socket*) node->value;
                if (socket->magic != JAVAN_SOCKET_MAGIC
                    || socket->fd < -1
                    || socket->connected < 0
                    || socket->closed < 0
                    || socket->local_port < 0
                    || socket->remote_port < 0
                    || socket->local_address == NULL
                    || socket->remote_address == NULL) {
                    javan_panic("invalid runtime socket metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SERVER_SOCKET) {
                javan_server_socket* socket = (javan_server_socket*) node->value;
                if (socket->magic != JAVAN_SERVER_SOCKET_MAGIC
                    || socket->fd < -1
                    || socket->closed < 0
                    || socket->local_port < 0
                    || socket->local_address == NULL) {
                    javan_panic("invalid runtime server socket metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM) {
                javan_socket_input_stream_value* stream = (javan_socket_input_stream_value*) node->value;
                if (stream->magic != JAVAN_SOCKET_INPUT_STREAM_MAGIC || stream->socket == NULL) {
                    javan_panic("invalid runtime socket input stream metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM) {
                javan_socket_output_stream_value* stream = (javan_socket_output_stream_value*) node->value;
                if (stream->magic != JAVAN_SOCKET_OUTPUT_STREAM_MAGIC || stream->socket == NULL) {
                    javan_panic("invalid runtime socket output stream metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_URI) {
                javan_uri_value* uri = (javan_uri_value*) node->value;
                if (uri->magic != JAVAN_URI_MAGIC
                    || uri->port < 0
                    || uri->scheme == NULL
                    || uri->host == NULL
                    || uri->target == NULL) {
                    javan_panic("invalid runtime uri metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CLIENT) {
                javan_http_client_value* client = (javan_http_client_value*) node->value;
                if (client->magic != JAVAN_HTTP_CLIENT_MAGIC) {
                    javan_panic("invalid runtime http client metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER) {
                javan_http_request_builder_value* builder = (javan_http_request_builder_value*) node->value;
                if (builder->magic != JAVAN_HTTP_REQUEST_BUILDER_MAGIC
                    || builder->uri == NULL
                    || builder->headers == NULL
                    || (builder->method != JAVAN_HTTP_METHOD_GET
                    && builder->method != JAVAN_HTTP_METHOD_POST
                    && builder->method != JAVAN_HTTP_METHOD_PUT)) {
                    javan_panic("invalid runtime http request builder metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST) {
                javan_http_request_value* request = (javan_http_request_value*) node->value;
                if (request->magic != JAVAN_HTTP_REQUEST_MAGIC
                    || request->uri == NULL
                    || request->headers == NULL
                    || (request->method != JAVAN_HTTP_METHOD_GET
                    && request->method != JAVAN_HTTP_METHOD_POST
                    && request->method != JAVAN_HTTP_METHOD_PUT)) {
                    javan_panic("invalid runtime http request metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER) {
                javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) node->value;
                if (publisher->magic != JAVAN_HTTP_BODY_PUBLISHER_MAGIC
                    || (publisher->kind != JAVAN_HTTP_BODY_KIND_STRING && publisher->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)
                    || publisher->value == NULL) {
                    javan_panic("invalid runtime http body publisher metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER) {
                javan_http_body_handler_value* body_handler = (javan_http_body_handler_value*) node->value;
                if (body_handler->magic != JAVAN_HTTP_BODY_HANDLER_MAGIC
                    || (body_handler->kind != JAVAN_HTTP_BODY_KIND_STRING && body_handler->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)) {
                    javan_panic("invalid runtime http body handler metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_RESPONSE) {
                javan_http_response_value* response = (javan_http_response_value*) node->value;
                if (response->magic != JAVAN_HTTP_RESPONSE_MAGIC || response->status_code < 0 || response->body == NULL) {
                    javan_panic("invalid runtime http response metadata");
                }
            }
        }

        void javan_validate_heap_metadata(void) {
            unsigned long live_allocations = 0;
            unsigned long live_bytes = 0;
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                if (node->value == NULL || node->base == NULL) {
                    javan_panic("invalid heap allocation metadata");
                }
                if (node->size == 0) {
                    javan_panic("invalid heap allocation size");
                }
                if (node->kind != JAVAN_HEAP_KIND_RUNTIME
                    && node->kind != JAVAN_HEAP_KIND_OBJECT
                    && node->kind != JAVAN_HEAP_KIND_ARRAY
                    && node->kind != JAVAN_HEAP_KIND_EXPORT) {
                    javan_panic("invalid heap allocation kind");
                }
                if (node->collectible != 0 && node->collectible != 1) {
                    javan_panic("invalid heap allocation collectibility");
                }
                if (node->runtime_kind != JAVAN_RUNTIME_KIND_NONE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_LIST
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_ITERATOR
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_MAP
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OPTIONAL
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_STRING
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_PROCESS_RESULT
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_STRING_BUILDER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OWNED_BUFFER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_INET_ADDRESS
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SOCKET
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SERVER_SOCKET
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_URI
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_CLIENT
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_REQUEST
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_RESPONSE) {
                    javan_panic("invalid runtime allocation kind");
                }
                javan_validate_runtime_container_references(node);
                live_allocations++;
                live_bytes += node->size;
                node = node->next;
            }
            if (live_allocations != javan_live_allocations_value || live_bytes != javan_live_allocated_bytes_value) {
                javan_panic("heap accounting mismatch");
            }
            if (javan_type_descriptor_count_value < 0) {
                javan_panic("invalid type descriptor count");
            }
            if (javan_type_descriptor_count_value > 0 && javan_type_descriptors_value == NULL) {
                javan_panic("invalid type descriptor inventory");
            }
            for (int index = 0; index < javan_type_descriptor_count_value; index++) {
                JavanTypeDescriptor descriptor = javan_type_descriptors_value[index];
                if (descriptor.type_id == 0 || descriptor.name == NULL || descriptor.object_field_count < 0) {
                    javan_panic("invalid type descriptor");
                }
                if (descriptor.object_field_count > 0 && descriptor.object_field_offsets == NULL) {
                    javan_panic("invalid type field descriptor");
                }
            }
            if (javan_static_root_count_value < 0) {
                javan_panic("invalid static root count");
            }
            if (javan_static_root_count_value > 0 && javan_static_roots_value == NULL) {
                javan_panic("invalid static root inventory");
            }
            for (int index = 0; index < javan_static_root_count_value; index++) {
                if (javan_static_roots_value[index] == NULL) {
                    javan_panic("invalid static root slot");
                }
            }
            int depth = 0;
            int root_count = 0;
            javan_root_frame* frame = javan_root_frames_value;
            while (frame != NULL) {
                if (frame->count < 0 || (frame->count > 0 && frame->roots == NULL)) {
                    javan_panic("invalid root frame");
                }
                for (int index = 0; index < frame->count; index++) {
                    if (frame->roots[index] == NULL) {
                        javan_panic("invalid root frame slot");
                    }
                }
                depth++;
                root_count += frame->count;
                frame = frame->next;
            }
            if (depth != javan_root_frame_depth_value || root_count != javan_frame_root_count_value) {
                javan_panic("root frame accounting mismatch");
            }
        }

        static void javan_heap_stress_init(void) {
            if (javan_heap_stress_initialized != 0) {
                return;
            }
            javan_heap_stress_initialized = 1;
            const char* value = getenv("JAVAN_GC_STRESS");
            if (value == NULL || value[0] == '\\0') {
                return;
            }
            char* end = NULL;
            unsigned long interval = strtoul(value, &end, 10);
            if (end == value || interval == 0) {
                interval = 1;
            }
            javan_heap_stress_interval = interval;
        }

        static void javan_heap_maybe_validate(void) {
            javan_heap_stress_init();
            if (javan_heap_stress_interval == 0) {
                return;
            }
            javan_heap_stress_ticks++;
            if ((javan_heap_stress_ticks % javan_heap_stress_interval) == 0) {
                javan_validate_heap_metadata();
            }
        }

        static void javan_allocation_limit_init(void) {
            if (javan_allocation_limit_initialized != 0) {
                return;
            }
            javan_allocation_limit_initialized = 1;
            const char* value = getenv("JAVAN_MAX_ALLOCATION_BYTES");
            char* end = NULL;
            unsigned long limit = 0;
            if (value != NULL && value[0] != '\\0') {
                limit = strtoul(value, &end, 10);
                if (end != value && limit > 0) {
                    javan_max_allocation_bytes = limit;
                }
            }
            value = getenv("JAVAN_HEAP_LIMIT_BYTES");
            if (value != NULL && value[0] != '\\0') {
                end = NULL;
                limit = strtoul(value, &end, 10);
                if (end != value && limit > 0) {
                    javan_heap_limit_bytes = limit;
                }
            }
        }

        static void javan_check_allocation_size(unsigned long size) {
            javan_allocation_limit_init();
            if (javan_max_allocation_bytes > 0 && size > javan_max_allocation_bytes) {
                javan_panic("out of memory");
            }
        }

        static int javan_heap_limit_exceeded(unsigned long size) {
            javan_allocation_limit_init();
            if (javan_heap_limit_bytes == 0 || javan_allocator_cleaning != 0) {
                return 0;
            }
            if (size > ULONG_MAX - javan_live_allocated_bytes_value) {
                return 1;
            }
            return javan_live_allocated_bytes_value + size > javan_heap_limit_bytes;
        }

        static int javan_heap_limit_growth_exceeded(unsigned long old_size, unsigned long new_size) {
            javan_allocation_limit_init();
            if (javan_heap_limit_bytes == 0 || javan_allocator_cleaning != 0 || new_size <= old_size) {
                return 0;
            }
            unsigned long growth = new_size - old_size;
            if (growth > ULONG_MAX - javan_live_allocated_bytes_value) {
                return 1;
            }
            return javan_live_allocated_bytes_value + growth > javan_heap_limit_bytes;
        }

        static void javan_prepare_allocation(unsigned long size) {
            javan_check_allocation_size(size);
            if (javan_heap_limit_exceeded(size)) {
                javan_gc_collect();
                if (javan_heap_limit_exceeded(size)) {
                    javan_panic("out of memory");
                }
            }
        }

        static void javan_prepare_reallocation(unsigned long old_size, unsigned long new_size) {
            javan_check_allocation_size(new_size);
            if (javan_heap_limit_growth_exceeded(old_size, new_size)) {
                javan_gc_collect();
                if (javan_heap_limit_growth_exceeded(old_size, new_size)) {
                    javan_panic("out of memory");
                }
            }
        }

        static void* javan_calloc_checked(unsigned long size) {
            void* value = calloc(1, size);
            if (value == NULL) {
                javan_gc_collect();
                value = calloc(1, size);
                if (value == NULL) {
                    javan_panic("out of memory");
                }
            }
            return value;
        }

        static void* javan_raw_calloc_retry(unsigned long size) {
            void* value = calloc(1, size);
            if (value == NULL) {
                javan_gc_collect();
                value = calloc(1, size);
            }
            return value;
        }
        """;

    private static final String SOURCE_HEAP_ALLOC = """
        void* javan_alloc(unsigned long size) {
            unsigned long actual_size = size == 0 ? 1 : size;
            javan_prepare_allocation(actual_size);
            void* value = javan_calloc_checked(actual_size);
            if (javan_allocator_cleaning == 0) {
                javan_track_allocation(value, value, actual_size, JAVAN_HEAP_KIND_RUNTIME, 0);
            }
            return value;
        }

        static char* javan_string_alloc(unsigned long size) {
            char* value = (char*) javan_alloc(size);
            javan_update_runtime_allocation_kind((void*) value, JAVAN_RUNTIME_KIND_STRING);
            return value;
        }

        static void* javan_export_alloc(unsigned long size) {
            unsigned long actual_size = size == 0 ? 1 : size;
            javan_prepare_allocation(actual_size);
            if (actual_size > ULONG_MAX - sizeof(javan_export_header)) {
                javan_panic("out of memory");
            }
            unsigned long total_size = actual_size + sizeof(javan_export_header);
            javan_export_header* header = (javan_export_header*) javan_calloc_checked(total_size);
            header->magic = JAVAN_EXPORT_ALLOCATION_MAGIC;
            header->size = actual_size;
            void* value = (void*) (header + 1);
            if (javan_allocator_cleaning == 0) {
                javan_track_allocation(value, (void*) header, actual_size, JAVAN_HEAP_KIND_EXPORT, 0);
            }
            return value;
        }

        static void* javan_realloc_tracked(void* value, unsigned long size, int validate_after) {
            if (value == NULL) {
                return javan_alloc(size);
            }
            unsigned long actual_size = size == 0 ? 1 : size;
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_panic("unknown runtime allocation");
            }
            if (node->base != value) {
                javan_panic("cannot reallocate exported runtime allocation");
            }
            javan_prepare_reallocation(node->size, actual_size);
            void* next = realloc(value, actual_size);
            if (next == NULL) {
                javan_gc_collect();
                next = realloc(value, actual_size);
                if (next == NULL) {
                    javan_panic("out of memory");
                }
            }
            javan_account_realloc(node->size, actual_size);
            node->value = next;
            node->base = next;
            node->size = actual_size;
            if (validate_after != 0) {
                javan_heap_maybe_validate();
            }
            return next;
        }

        static void* javan_realloc(void* value, unsigned long size) {
            return javan_realloc_tracked(value, size, 1);
        }

        static void* javan_realloc_owned_buffer(void* value, unsigned long size) {
            return javan_realloc_tracked(value, size, 0);
        }

        static void javan_object_registry_remove(void* value);

        static void javan_free_owned_runtime_buffer(void* value) {
            if (value == NULL) {
                return;
            }
            javan_allocation_node* previous = NULL;
            javan_allocation_node* node = javan_find_allocation(value, &previous);
            if (node == NULL) {
                return;
            }
            if (node->kind != JAVAN_HEAP_KIND_RUNTIME || node->runtime_kind != JAVAN_RUNTIME_KIND_OWNED_BUFFER) {
                javan_panic("invalid owned runtime buffer");
            }
            if (previous == NULL) {
                javan_allocations = node->next;
            } else {
                previous->next = node->next;
            }
            unsigned long size = node->size;
            void* base = node->base;
            free(node);
            free(base);
            javan_account_free(size);
        }

        static void javan_release_runtime_owned_buffers(javan_allocation_node* node) {
            if (node == NULL || node->kind != JAVAN_HEAP_KIND_RUNTIME) {
                return;
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST) {
                javan_object_list* list = (javan_object_list*) node->value;
                if (list != NULL && list->magic == JAVAN_OBJECT_LIST_MAGIC) {
                    javan_free_owned_runtime_buffer((void*) list->values);
                    list->values = NULL;
                    list->capacity = 0;
                    list->length = 0;
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP) {
                javan_object_map* map = (javan_object_map*) node->value;
                if (map != NULL && map->magic == JAVAN_OBJECT_MAP_MAGIC) {
                    javan_free_owned_runtime_buffer((void*) map->keys);
                    javan_free_owned_runtime_buffer((void*) map->values);
                    map->keys = NULL;
                    map->values = NULL;
                    map->capacity = 0;
                    map->length = 0;
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                javan_string_builder* builder = (javan_string_builder*) node->value;
                if (builder != NULL && builder->magic == JAVAN_STRING_BUILDER_MAGIC) {
                    javan_free_owned_runtime_buffer((void*) builder->values);
                    builder->values = NULL;
                    builder->capacity = 0;
                    builder->length = 0;
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_RESULT) {
                javan_process_result* result = (javan_process_result*) node->value;
                if (result != NULL) {
                    char* stdout_value = result->stdout_value;
                    char* stderr_value = result->stderr_value;
                    result->stdout_value = NULL;
                    result->stderr_value = NULL;
                    javan_free(stdout_value);
                    if (stderr_value != stdout_value) {
                        javan_free(stderr_value);
                    }
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SOCKET) {
                javan_socket* socket = (javan_socket*) node->value;
                if (socket != NULL && socket->fd >= 0) {
                    javan_socket_native_close(socket->fd);
                    socket->fd = -1;
                    socket->closed = 1;
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SERVER_SOCKET) {
                javan_server_socket* socket = (javan_server_socket*) node->value;
                if (socket != NULL && socket->fd >= 0) {
                    javan_socket_native_close(socket->fd);
                    socket->fd = -1;
                    socket->closed = 1;
                }
            }
        }

        void javan_free(void* value) {
            if (value == NULL) {
                return;
            }
            javan_allocation_node* previous = NULL;
            javan_allocation_node* node = javan_find_allocation(value, &previous);
            if (node == NULL) {
                javan_panic("unknown runtime allocation");
            }
            javan_release_runtime_owned_buffers(node);
            previous = NULL;
            node = javan_find_allocation(value, &previous);
            if (node == NULL) {
                return;
            }
            if (previous == NULL) {
                javan_allocations = node->next;
            } else {
                previous->next = node->next;
            }
            if (node->kind == JAVAN_HEAP_KIND_OBJECT) {
                javan_object_registry_remove(value);
            }
            void* base = node->base;
            javan_account_free(node->size);
            free(node);
            free(base);
            javan_heap_maybe_validate();
        }

        typedef struct {
            void** values;
            int* type_ids;
            int length;
            int capacity;
        } javan_object_registry;

        static javan_object_registry javan_objects = { NULL, NULL, 0, 0 };

        static unsigned long javan_registry_hash(void* value) {
            uintptr_t raw = (uintptr_t) value;
            raw >>= 3;
            raw ^= raw >> 17;
            raw *= (uintptr_t) 0xed5ad4bbU;
            raw ^= raw >> 11;
            return (unsigned long) raw;
        }

        static int javan_registry_slot(void** values, int capacity, void* value) {
            unsigned long hash = javan_registry_hash(value);
            int index = (int) (hash & (unsigned long) (capacity - 1));
            while (values[index] != NULL && values[index] != value) {
                index = (index + 1) & (capacity - 1);
            }
            return index;
        }

        static void javan_object_registry_reinsert(void** values, int* type_ids, int capacity, void* value, int type_id) {
            int index = javan_registry_slot(values, capacity, value);
            values[index] = value;
            type_ids[index] = type_id;
        }

        static void javan_object_registry_ensure_capacity(int required) {
            if ((required * 2) < javan_objects.capacity) {
                return;
            }
            int old_capacity = javan_objects.capacity;
            void** old_values = javan_objects.values;
            int* old_type_ids = javan_objects.type_ids;
            int next_capacity = old_capacity <= 0 ? 128 : old_capacity * 2;
            while ((required * 2) >= next_capacity) {
                next_capacity *= 2;
            }
            void** next_values = (void**) javan_raw_calloc_retry((unsigned long) next_capacity * sizeof(void*));
            if (next_values == NULL) {
                javan_panic("out of memory");
            }
            int* next_type_ids = (int*) javan_raw_calloc_retry((unsigned long) next_capacity * sizeof(int));
            if (next_type_ids == NULL) {
                free(next_values);
                javan_panic("out of memory");
            }
            for (int index = 0; index < old_capacity; index++) {
                if (old_values != NULL && old_values[index] != NULL) {
                    javan_object_registry_reinsert(next_values, next_type_ids, next_capacity, old_values[index], old_type_ids[index]);
                }
            }
            free(old_values);
            free(old_type_ids);
            javan_objects.values = next_values;
            javan_objects.type_ids = next_type_ids;
            javan_objects.capacity = next_capacity;
        }

        static void javan_object_registry_cleanup(void) {
            free(javan_objects.values);
            free(javan_objects.type_ids);
            javan_objects.values = NULL;
            javan_objects.type_ids = NULL;
            javan_objects.length = 0;
            javan_objects.capacity = 0;
        }

        static void javan_object_registry_remove(void* value) {
            if (value == NULL || javan_objects.capacity <= 0) {
                return;
            }
            int index = javan_registry_slot(javan_objects.values, javan_objects.capacity, value);
            if (javan_objects.values[index] != value) {
                return;
            }
            javan_objects.values[index] = NULL;
            javan_objects.type_ids[index] = 0;
            javan_objects.length--;
            int next = (index + 1) & (javan_objects.capacity - 1);
            while (javan_objects.values[next] != NULL) {
                void* moved_value = javan_objects.values[next];
                int moved_type_id = javan_objects.type_ids[next];
                javan_objects.values[next] = NULL;
                javan_objects.type_ids[next] = 0;
                javan_objects.length--;
                javan_object_registry_reinsert(javan_objects.values, javan_objects.type_ids, javan_objects.capacity, moved_value, moved_type_id);
                javan_objects.length++;
                next = (next + 1) & (javan_objects.capacity - 1);
            }
        }

        void javan_register_object(void* value, int type_id) {
            if (value == NULL || type_id == 0) {
                return;
            }
            javan_object_registry_ensure_capacity(javan_objects.length + 1);
            javan_update_allocation_metadata(value, JAVAN_HEAP_KIND_OBJECT, type_id);
            int index = javan_registry_slot(javan_objects.values, javan_objects.capacity, value);
            if (javan_objects.values[index] == NULL) {
                javan_objects.length++;
            }
            javan_objects.values[index] = value;
            javan_objects.type_ids[index] = type_id;
        }

        static int javan_registered_type_id(void* value) {
            if (value == NULL) {
                return 0;
            }
            if (javan_objects.capacity <= 0) {
                return 0;
            }
            int index = javan_registry_slot(javan_objects.values, javan_objects.capacity, value);
            return javan_objects.values[index] == value ? javan_objects.type_ids[index] : 0;
        }

        int javan_object_non_null(void* value) {
            return value != NULL;
        }

        int javan_object_type_in(void* value, int count, ...) {
            if (value == NULL || count <= 0) {
                return 0;
            }
            int type_id = javan_registered_type_id(value);
            if (type_id == 0) {
                return 0;
            }
            va_list arguments;
            va_start(arguments, count);
            for (int index = 0; index < count; index++) {
                int accepted = va_arg(arguments, int);
                if (type_id == accepted) {
                    va_end(arguments);
                    return 1;
                }
            }
            va_end(arguments);
            return 0;
        }

        typedef struct {
            int value;
        } javan_boxed_int;

        typedef struct {
            long long value;
        } javan_boxed_long;

        typedef struct {
            float value;
        } javan_boxed_float;

        typedef struct {
            double value;
        } javan_boxed_double;

        typedef struct {
            int value;
        } javan_boxed_boolean;

        typedef struct {
            long long millis;
        } javan_file_time;

        typedef struct {
            long long seconds;
            int nanos;
            int exact_millis;
            long long millis;
        } javan_duration;

        typedef struct {
            int interrupted;
        } javan_thread;

        void* javan_thread_new(void) {
            javan_thread* object = (javan_thread*) javan_alloc(sizeof(javan_thread));
            object->interrupted = 0;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_THREAD);
            return object;
        }

        void* javan_integer_value_of(int value) {
            javan_boxed_int* object = (javan_boxed_int*) javan_alloc(sizeof(javan_boxed_int));
            object->value = value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_INTEGER);
            return object;
        }

        int javan_integer_int_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_INTEGER) {
                javan_panic("not an Integer");
            }
            return ((javan_boxed_int*) value)->value;
        }

        void* javan_long_value_of(long long value) {
            javan_boxed_long* object = (javan_boxed_long*) javan_alloc(sizeof(javan_boxed_long));
            object->value = value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_LONG);
            return object;
        }

        long long javan_long_long_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_LONG) {
                javan_panic("not a Long");
            }
            return ((javan_boxed_long*) value)->value;
        }

        void* javan_float_value_of(float value) {
            javan_boxed_float* object = (javan_boxed_float*) javan_alloc(sizeof(javan_boxed_float));
            object->value = value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_FLOAT);
            return object;
        }

        float javan_float_float_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_FLOAT) {
                javan_panic("not a Float");
            }
            return ((javan_boxed_float*) value)->value;
        }

        float javan_float_int_bits_to_float(int value) {
            float result;
            memcpy(&result, &value, sizeof(float));
            return result;
        }

        void* javan_double_value_of(double value) {
            javan_boxed_double* object = (javan_boxed_double*) javan_alloc(sizeof(javan_boxed_double));
            object->value = value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_DOUBLE);
            return object;
        }

        double javan_double_double_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                javan_panic("not a Double");
            }
            return ((javan_boxed_double*) value)->value;
        }

        double javan_double_long_bits_to_double(long long value) {
            double result;
            memcpy(&result, &value, sizeof(double));
            return result;
        }

        void* javan_boolean_value_of(int value) {
            javan_boxed_boolean* object = (javan_boxed_boolean*) javan_alloc(sizeof(javan_boxed_boolean));
            object->value = value != 0 ? 1 : 0;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_BOOLEAN);
            return object;
        }

        int javan_boolean_boolean_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                javan_panic("not a Boolean");
            }
            return ((javan_boxed_boolean*) value)->value;
        }

        static void* javan_file_time_from_millis(long long millis) {
            javan_file_time* object = (javan_file_time*) javan_alloc(sizeof(javan_file_time));
            object->millis = millis;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME);
            return object;
        }

        long long javan_file_time_to_millis(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME) {
                javan_panic("not a FileTime");
            }
            return ((javan_file_time*) value)->millis;
        }

        static void* javan_duration_from_parts(long long seconds, int nanos, int exact_millis, long long millis) {
            javan_duration* object = (javan_duration*) javan_alloc(sizeof(javan_duration));
            object->seconds = seconds;
            object->nanos = nanos;
            object->exact_millis = exact_millis;
            object->millis = millis;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_TIME_DURATION);
            return object;
        }

        void* javan_duration_of_millis(long long millis) {
            long long seconds = millis / 1000LL;
            int remainder = (int) (millis % 1000LL);
            if (remainder < 0) {
                remainder += 1000;
                seconds -= 1;
            }
            return javan_duration_from_parts(seconds, remainder * 1000000, 1, millis);
        }

        void* javan_duration_of_seconds(long long seconds) {
            return javan_duration_from_parts(seconds, 0, 0, 0);
        }

        long long javan_duration_to_millis(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_TIME_DURATION) {
                javan_panic("not a Duration");
            }
            javan_duration* duration = (javan_duration*) value;
            if (duration->exact_millis != 0) {
                return duration->millis;
            }
            if (duration->seconds > LLONG_MAX / 1000LL || duration->seconds < LLONG_MIN / 1000LL) {
                javan_panic("duration toMillis overflow");
            }
            return (duration->seconds * 1000LL) + ((long long) duration->nanos / 1000000LL);
        }

        static javan_thread* javan_require_thread(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_THREAD) {
                javan_panic("not a Thread");
            }
            return (javan_thread*) value;
        }

        static javan_thread* javan_current_thread_object(void) {
            if (javan_current_thread_value == NULL) {
                javan_current_thread_value = javan_thread_new();
            }
            return (javan_thread*) javan_current_thread_value;
        }

        void* javan_thread_current(void) {
            return (void*) javan_current_thread_object();
        }

        void javan_thread_sleep_millis(long long millis) {
            if (millis < 0) {
                javan_panic("negative Thread.sleep millis");
            }
            javan_thread* thread = javan_current_thread_object();
            if (thread->interrupted != 0) {
                thread->interrupted = 0;
                javan_panic("Thread.sleep interrupted is not supported yet");
            }
            while (millis > 0) {
                long long chunk = millis > 60000LL ? 60000LL : millis;
                usleep((useconds_t) (chunk * 1000LL));
                millis -= chunk;
            }
        }

        int javan_thread_interrupted(void) {
            javan_thread* thread = javan_current_thread_object();
            int interrupted = thread->interrupted;
            thread->interrupted = 0;
            return interrupted;
        }

        void javan_thread_interrupt(void* value) {
            javan_require_thread(value)->interrupted = 1;
        }

        int javan_thread_is_interrupted(void* value) {
            return javan_require_thread(value)->interrupted;
        }

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
        } javan_array_header;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            void* values[];
        } javan_object_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            int values[];
        } javan_int_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            long long values[];
        } javan_long_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            float values[];
        } javan_float_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            double values[];
        } javan_double_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            signed char values[];
        } javan_byte_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            short values[];
        } javan_short_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            unsigned short values[];
        } javan_char_array;

        static JavanTypeDescriptor* javan_type_descriptor_for(int type_id) {
            for (int index = 0; index < javan_type_descriptor_count_value; index++) {
                if (javan_type_descriptors_value[index].type_id == type_id) {
                    return &javan_type_descriptors_value[index];
                }
            }
            return NULL;
        }

        static void javan_gc_mark_value(void* value);
        static void javan_gc_mark_runtime_object_references(void);

        static void javan_gc_mark_object_fields(void* value, int type_id) {
            JavanTypeDescriptor* descriptor = javan_type_descriptor_for(type_id);
            if (descriptor == NULL || descriptor->object_field_count <= 0) {
                return;
            }
            for (int index = 0; index < descriptor->object_field_count; index++) {
                unsigned long offset = descriptor->object_field_offsets[index];
                void** field = (void**) (((char*) value) + offset);
                javan_gc_mark_value(*field);
            }
        }

        static void javan_gc_mark_object_array(javan_object_array* array) {
            if (array == NULL) {
                return;
            }
            for (int index = 0; index < array->length; index++) {
                javan_gc_mark_value(array->values[index]);
            }
        }

        static void javan_gc_mark_runtime_list(javan_object_list* list) {
            if (list == NULL || list->magic != JAVAN_OBJECT_LIST_MAGIC) {
                return;
            }
            javan_gc_mark_value((void*) list->values);
            for (int index = 0; index < list->length; index++) {
                javan_gc_mark_value(list->values[index]);
            }
        }

        static void javan_gc_mark_runtime_map(javan_object_map* map) {
            if (map == NULL || map->magic != JAVAN_OBJECT_MAP_MAGIC) {
                return;
            }
            javan_gc_mark_value((void*) map->keys);
            javan_gc_mark_value((void*) map->values);
            for (int index = 0; index < map->length; index++) {
                javan_gc_mark_value(map->keys[index]);
                javan_gc_mark_value(map->values[index]);
            }
        }

        static void javan_gc_mark_runtime_children(void* value, int runtime_kind) {
            if (value == NULL) {
                return;
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST) {
                javan_gc_mark_runtime_list((javan_object_list*) value);
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_ITERATOR) {
                javan_object_iterator* iterator = (javan_object_iterator*) value;
                if (iterator != NULL && iterator->magic == JAVAN_OBJECT_ITERATOR_MAGIC) {
                    javan_gc_mark_value((void*) iterator->list);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP) {
                javan_gc_mark_runtime_map((javan_object_map*) value);
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_OPTIONAL) {
                javan_optional* optional = (javan_optional*) value;
                if (optional != NULL && optional->magic == JAVAN_OPTIONAL_MAGIC && optional->present != 0) {
                    javan_gc_mark_value(optional->value);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                javan_string_builder* builder = (javan_string_builder*) value;
                if (builder != NULL && builder->magic == JAVAN_STRING_BUILDER_MAGIC) {
                    javan_gc_mark_value((void*) builder->values);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_INET_ADDRESS) {
                javan_inet_address* address = (javan_inet_address*) value;
                if (address != NULL && address->magic == JAVAN_INET_ADDRESS_MAGIC) {
                    javan_gc_mark_value((void*) address->host_address);
                    javan_gc_mark_value((void*) address->host_name);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS) {
                javan_inet_socket_address* address = (javan_inet_socket_address*) value;
                if (address != NULL && address->magic == JAVAN_INET_SOCKET_ADDRESS_MAGIC) {
                    javan_gc_mark_value((void*) address->address);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_SOCKET) {
                javan_socket* socket = (javan_socket*) value;
                if (socket != NULL && socket->magic == JAVAN_SOCKET_MAGIC) {
                    javan_gc_mark_value((void*) socket->local_address);
                    javan_gc_mark_value((void*) socket->remote_address);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_SERVER_SOCKET) {
                javan_server_socket* socket = (javan_server_socket*) value;
                if (socket != NULL && socket->magic == JAVAN_SERVER_SOCKET_MAGIC) {
                    javan_gc_mark_value((void*) socket->local_address);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM) {
                javan_socket_input_stream_value* stream = (javan_socket_input_stream_value*) value;
                if (stream != NULL && stream->magic == JAVAN_SOCKET_INPUT_STREAM_MAGIC) {
                    javan_gc_mark_value((void*) stream->socket);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM) {
                javan_socket_output_stream_value* stream = (javan_socket_output_stream_value*) value;
                if (stream != NULL && stream->magic == JAVAN_SOCKET_OUTPUT_STREAM_MAGIC) {
                    javan_gc_mark_value((void*) stream->socket);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_URI) {
                javan_uri_value* uri = (javan_uri_value*) value;
                if (uri != NULL && uri->magic == JAVAN_URI_MAGIC) {
                    javan_gc_mark_value((void*) uri->scheme);
                    javan_gc_mark_value((void*) uri->host);
                    javan_gc_mark_value((void*) uri->target);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER) {
                javan_http_request_builder_value* builder = (javan_http_request_builder_value*) value;
                if (builder != NULL && builder->magic == JAVAN_HTTP_REQUEST_BUILDER_MAGIC) {
                    javan_gc_mark_value((void*) builder->uri);
                    javan_gc_mark_value((void*) builder->headers);
                    javan_gc_mark_value(builder->body);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST) {
                javan_http_request_value* request = (javan_http_request_value*) value;
                if (request != NULL && request->magic == JAVAN_HTTP_REQUEST_MAGIC) {
                    javan_gc_mark_value((void*) request->uri);
                    javan_gc_mark_value((void*) request->headers);
                    javan_gc_mark_value(request->body);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER) {
                javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) value;
                if (publisher != NULL && publisher->magic == JAVAN_HTTP_BODY_PUBLISHER_MAGIC) {
                    javan_gc_mark_value(publisher->value);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_RESPONSE) {
                javan_http_response_value* response = (javan_http_response_value*) value;
                if (response != NULL && response->magic == JAVAN_HTTP_RESPONSE_MAGIC) {
                    javan_gc_mark_value((void*) response->body);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_RESULT) {
                javan_process_result* result = (javan_process_result*) value;
                javan_gc_mark_value(result->stdout_value);
                javan_gc_mark_value(result->stderr_value);
            }
        }

        static void javan_gc_mark_value(void* value) {
            if (value == NULL) {
                return;
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                return;
            }
            if (node->mark != 0) {
                return;
            }
            node->mark = 1;
            if (node->kind == JAVAN_HEAP_KIND_OBJECT) {
                javan_gc_mark_object_fields(value, node->type_id);
                return;
            }
            if (node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_OBJECT) {
                javan_gc_mark_object_array((javan_object_array*) value);
                return;
            }
            if (node->kind == JAVAN_HEAP_KIND_RUNTIME) {
                javan_gc_mark_runtime_children(value, node->runtime_kind);
            }
        }

        static void javan_gc_mark_static_roots(void) {
            for (int index = 0; index < javan_static_root_count_value; index++) {
                void** slot = javan_static_roots_value[index];
                if (slot != NULL) {
                    javan_gc_mark_value(*slot);
                }
            }
            javan_gc_mark_value(javan_current_thread_value);
        }

        static void javan_gc_mark_frame_roots(void) {
            javan_root_frame* frame = javan_root_frames_value;
            while (frame != NULL) {
                for (int index = 0; index < frame->count; index++) {
                    void** slot = frame->roots[index];
                    if (slot != NULL) {
                        javan_gc_mark_value(*slot);
                    }
                }
                frame = frame->next;
            }
        }

        static void javan_gc_mark_runtime_object_references(void) {
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                if (node->kind == JAVAN_HEAP_KIND_RUNTIME && node->mark != 0) {
                    javan_gc_mark_runtime_children(node->value, node->runtime_kind);
                }
                node = node->next;
            }
        }

        static void javan_gc_clear_marks(void) {
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                node->mark = 0;
                node = node->next;
            }
        }

        static void javan_gc_sweep_unmarked(void) {
            javan_allocation_node* previous = NULL;
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                javan_allocation_node* next = node->next;
                if (node->collectible != 0 && node->mark == 0) {
                    if (node->kind == JAVAN_HEAP_KIND_OBJECT) {
                        javan_object_registry_remove(node->value);
                    }
                    if (previous == NULL) {
                        javan_allocations = next;
                    } else {
                        previous->next = next;
                    }
                    unsigned long size = node->size;
                    void* base = node->base;
                    free(node);
                    free(base);
                    javan_account_free(size);
                    javan_gc_collected_allocations_value++;
                    javan_gc_collected_bytes_value += size;
                } else {
                    previous = node;
                }
                node = next;
            }
        }

        void javan_gc_collect(void) {
            if (javan_gc_enabled_value == 0 || javan_gc_collecting != 0 || javan_allocator_cleaning != 0) {
                return;
            }
            javan_gc_collecting = 1;
            javan_gc_collection_count_value++;
            javan_gc_clear_marks();
            javan_gc_mark_static_roots();
            javan_gc_mark_frame_roots();
            javan_gc_mark_runtime_object_references();
            javan_gc_sweep_unmarked();
            javan_gc_collecting = 0;
            javan_heap_maybe_validate();
        }

        static void javan_gc_safe_point_init(void) {
            if (javan_gc_safe_point_initialized != 0) {
                return;
            }
            javan_gc_safe_point_initialized = 1;
            const char* value = getenv("JAVAN_GC_SAFEPOINT_INTERVAL");
            if (value == NULL || value[0] == '\\0') {
                return;
            }
            char* end = NULL;
            unsigned long interval = strtoul(value, &end, 10);
            if (end == value || interval == 0) {
                interval = 1;
            }
            javan_gc_safe_point_interval = interval;
        }

        void javan_gc_safe_point(void) {
            if (javan_gc_enabled_value == 0 || javan_allocator_cleaning != 0) {
                return;
            }
            javan_gc_safe_point_init();
            if (javan_gc_safe_point_interval == 0) {
                return;
            }
            javan_gc_safe_point_ticks++;
            if ((javan_gc_safe_point_ticks % javan_gc_safe_point_interval) == 0) {
                javan_gc_collect();
            }
        }
        """;

    private static final String SOURCE_ARRAYS = """
        static void javan_array_init(javan_array_header* array, int length, int element_size, int kind) {
            array->length = length;
            array->element_size = element_size;
            array->kind = kind;
            array->reserved = 0;
            javan_update_allocation_metadata((void*) array, JAVAN_HEAP_KIND_ARRAY, kind);
        }

        static unsigned long javan_array_allocation_size(unsigned long header_size, int length, unsigned long element_size) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            if (element_size > 0 && (unsigned long) length > (ULONG_MAX - header_size) / element_size) {
                javan_panic("array allocation too large");
            }
            return header_size + ((unsigned long) length * element_size);
        }

        void* javan_object_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_object_array), length, sizeof(void*));
            javan_object_array* array = (javan_object_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(void*), JAVAN_ARRAY_KIND_OBJECT);
            return array;
        }

        void* javan_int_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_int_array), length, sizeof(int));
            javan_int_array* array = (javan_int_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(int), JAVAN_ARRAY_KIND_INT);
            return array;
        }

        void* javan_long_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_long_array), length, sizeof(long long));
            javan_long_array* array = (javan_long_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(long long), JAVAN_ARRAY_KIND_LONG);
            return array;
        }

        void* javan_float_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_float_array), length, sizeof(float));
            javan_float_array* array = (javan_float_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(float), JAVAN_ARRAY_KIND_FLOAT);
            return array;
        }

        void* javan_double_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_double_array), length, sizeof(double));
            javan_double_array* array = (javan_double_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(double), JAVAN_ARRAY_KIND_DOUBLE);
            return array;
        }

        void* javan_byte_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_byte_array), length, sizeof(signed char));
            javan_byte_array* array = (javan_byte_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(signed char), JAVAN_ARRAY_KIND_BYTE);
            return array;
        }

        void* javan_boolean_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_byte_array), length, sizeof(signed char));
            javan_byte_array* array = (javan_byte_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(signed char), JAVAN_ARRAY_KIND_BOOLEAN);
            return array;
        }

        void* javan_short_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_short_array), length, sizeof(short));
            javan_short_array* array = (javan_short_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(short), JAVAN_ARRAY_KIND_SHORT);
            return array;
        }

        void* javan_char_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_char_array), length, sizeof(unsigned short));
            javan_char_array* array = (javan_char_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(unsigned short), JAVAN_ARRAY_KIND_CHAR);
            return array;
        }

        static javan_array_header* javan_array_checked(void* array) {
            if (array == NULL) {
                javan_panic("null array");
            }
            return (javan_array_header*) array;
        }

        static void javan_array_bounds_checked(javan_array_header* array, int index) {
            if (index < 0 || index >= array->length) {
                javan_panic("array index out of bounds");
            }
        }

        static void javan_array_range_checked(javan_array_header* array, int position, int length) {
            if (position < 0 || length < 0 || position > array->length || length > array->length - position) {
                javan_panic("array copy out of bounds");
            }
        }

        static void javan_array_kind_checked(javan_array_header* array, int expected_kind) {
            if (array->kind != expected_kind) {
                javan_panic("array copy type mismatch");
            }
        }

        static void* javan_array_values(javan_array_header* array) {
            return ((char*) array) + sizeof(javan_array_header);
        }

        void javan_system_arraycopy(void* source, int source_position, void* target, int target_position, int length) {
            javan_array_header* source_array = javan_array_checked(source);
            javan_array_header* target_array = javan_array_checked(target);
            if (source_array->kind != target_array->kind || source_array->element_size != target_array->element_size) {
                javan_panic("array copy type mismatch");
            }
            javan_array_range_checked(source_array, source_position, length);
            javan_array_range_checked(target_array, target_position, length);
            if (length == 0) {
                return;
            }
            memmove(
                ((char*) javan_array_values(target_array)) + ((unsigned long) target_position * (unsigned long) target_array->element_size),
                ((char*) javan_array_values(source_array)) + ((unsigned long) source_position * (unsigned long) source_array->element_size),
                (unsigned long) length * (unsigned long) source_array->element_size
            );
        }

        void javan_system_exit(int status) {
            exit(status);
        }

        void* javan_object_array_get(void* array, int index) {
            javan_object_array* values = (javan_object_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_object_array_set(void* array, int index, void* value) {
            javan_object_array* values = (javan_object_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = value;
        }

        int javan_int_array_get(void* array, int index) {
            javan_int_array* values = (javan_int_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_int_array_set(void* array, int index, int value) {
            javan_int_array* values = (javan_int_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = value;
        }

        long long javan_long_array_get(void* array, int index) {
            javan_long_array* values = (javan_long_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_long_array_set(void* array, int index, long long value) {
            javan_long_array* values = (javan_long_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = value;
        }

        float javan_float_array_get(void* array, int index) {
            javan_float_array* values = (javan_float_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_float_array_set(void* array, int index, float value) {
            javan_float_array* values = (javan_float_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = value;
        }

        double javan_double_array_get(void* array, int index) {
            javan_double_array* values = (javan_double_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_double_array_set(void* array, int index, double value) {
            javan_double_array* values = (javan_double_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = value;
        }

        int javan_byte_array_get(void* array, int index) {
            javan_byte_array* values = (javan_byte_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_byte_array_set(void* array, int index, int value) {
            javan_byte_array* values = (javan_byte_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = (signed char) value;
        }

        void* javan_byte_array_from(const signed char* data, int length) {
            if (length < 0) {
                javan_panic("negative byte array length");
            }
            if (data == NULL && length > 0) {
                javan_panic("null byte array input");
            }
            javan_byte_array* result = (javan_byte_array*) javan_byte_array_new(length);
            if (length > 0) {
                memcpy(result->values, data, (unsigned long) length);
            }
            return result;
        }

        JavanByteArray javan_byte_array_export(void* array) {
            javan_byte_array* values = (javan_byte_array*) javan_array_checked(array);
            JavanByteArray result;
            result.length = values->length;
            result.data = NULL;
            if (values->length > 0) {
                void* array_root = array;
                void** javan_byte_export_roots[] = {
                    (void**) &array_root
                };
                javan_root_frame_push(javan_byte_export_roots, 1);
                values = (javan_byte_array*) javan_array_checked(array_root);
                result.data = (signed char*) javan_export_alloc((unsigned long) values->length);
                memcpy(result.data, values->values, (unsigned long) values->length);
                javan_root_frame_pop(javan_byte_export_roots);
            }
            return result;
        }

        int javan_short_array_get(void* array, int index) {
            javan_short_array* values = (javan_short_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_short_array_set(void* array, int index, int value) {
            javan_short_array* values = (javan_short_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = (short) value;
        }

        int javan_char_array_get(void* array, int index) {
            javan_char_array* values = (javan_char_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            return values->values[index];
        }

        void javan_char_array_set(void* array, int index, int value) {
            javan_char_array* values = (javan_char_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = (unsigned short) value;
        }

        int javan_array_length(void* array) {
            return javan_array_checked(array)->length;
        }

        static void* javan_arrays_copy_of(void* array, int new_length, int expected_kind, void* (*allocate)(int)) {
            void* source_root = array;
            javan_array_header* source = javan_array_checked(source_root);
            javan_array_kind_checked(source, expected_kind);
            void** javan_array_copy_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_array_copy_roots, 1);
            void* result = allocate(new_length);
            source = javan_array_checked(source_root);
            javan_array_header* target = javan_array_checked(result);
            int copied = source->length < new_length ? source->length : new_length;
            if (copied > 0) {
                memcpy(
                    javan_array_values(target),
                    javan_array_values(source),
                    (unsigned long) copied * (unsigned long) source->element_size
                );
            }
            javan_root_frame_pop(javan_array_copy_roots);
            return result;
        }

        void* javan_arrays_copy_of_object(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_OBJECT, javan_object_array_new);
        }

        void* javan_arrays_copy_of_int(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_INT, javan_int_array_new);
        }

        void* javan_arrays_copy_of_long(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_LONG, javan_long_array_new);
        }

        void* javan_arrays_copy_of_float(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_FLOAT, javan_float_array_new);
        }

        void* javan_arrays_copy_of_double(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_DOUBLE, javan_double_array_new);
        }

        void* javan_arrays_copy_of_byte(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_BYTE, javan_byte_array_new);
        }

        void* javan_arrays_copy_of_short(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_SHORT, javan_short_array_new);
        }

        void* javan_arrays_copy_of_char(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_CHAR, javan_char_array_new);
        }

        void* javan_arrays_copy_of_range_byte(void* array, int begin, int end) {
            void* source_root = array;
            javan_array_header* source = javan_array_checked(source_root);
            javan_array_kind_checked(source, JAVAN_ARRAY_KIND_BYTE);
            if (begin > end) {
                javan_panic("array range invalid");
            }
            if (begin < 0 || begin > source->length) {
                javan_panic("array copy out of bounds");
            }
            int new_length = end - begin;
            void** javan_array_range_copy_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_array_range_copy_roots, 1);
            void* result = javan_byte_array_new(new_length);
            source = javan_array_checked(source_root);
            javan_array_header* target = javan_array_checked(result);
            int remaining = source->length - begin;
            int copied = remaining < new_length ? remaining : new_length;
            if (copied > 0) {
                memcpy(
                    javan_array_values(target),
                    ((char*) javan_array_values(source)) + ((unsigned long) begin * (unsigned long) source->element_size),
                    (unsigned long) copied * (unsigned long) source->element_size
                );
            }
            javan_root_frame_pop(javan_array_range_copy_roots);
            return result;
        }

        void* javan_arrays_copy_of_range_object(void* array, int begin, int end) {
            void* source_root = array;
            javan_array_header* source = javan_array_checked(source_root);
            javan_array_kind_checked(source, JAVAN_ARRAY_KIND_OBJECT);
            if (begin > end) {
                javan_panic("array range invalid");
            }
            if (begin < 0 || begin > source->length) {
                javan_panic("array copy out of bounds");
            }
            int new_length = end - begin;
            void** javan_array_range_copy_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_array_range_copy_roots, 1);
            void* result = javan_object_array_new(new_length);
            source = javan_array_checked(source_root);
            javan_array_header* target = javan_array_checked(result);
            int remaining = source->length - begin;
            int copied = remaining < new_length ? remaining : new_length;
            if (copied > 0) {
                memcpy(
                    javan_array_values(target),
                    ((char*) javan_array_values(source)) + ((unsigned long) begin * (unsigned long) source->element_size),
                    (unsigned long) copied * (unsigned long) source->element_size
                );
            }
            javan_root_frame_pop(javan_array_range_copy_roots);
            return result;
        }

        void* javan_string_array_from_args(int argc, char** argv) {
            int length = argc > 0 ? argc - 1 : 0;
            void* result = javan_object_array_new(length);
            for (int index = 0; index < length; index++) {
                javan_object_array_set(result, index, argv[index + 1]);
            }
            return result;
        }

        static int javan_utf8_length_from_utf16(const unsigned short* values, int offset, int count) {
            int length = 0;
            int index = offset;
            int end = offset + count;
            while (index < end) {
                unsigned int ch = values[index];
                if (ch == 0) {
                    javan_panic("unsupported null character in string");
                }
                if (ch <= 0x7F) {
                    length++;
                } else if (ch <= 0x7FF) {
                    length += 2;
                } else if (ch >= 0xD800 && ch <= 0xDBFF && index + 1 < end
                    && values[index + 1] >= 0xDC00 && values[index + 1] <= 0xDFFF) {
                    length += 4;
                    index++;
                } else {
                    length += 3;
                }
                index++;
            }
            return length;
        }

        static char* javan_utf8_write_from_utf16(char* out, const unsigned short* values, int offset, int count) {
            int index = offset;
            int end = offset + count;
            while (index < end) {
                unsigned int ch = values[index];
                if (ch <= 0x7F) {
                    *out++ = (char) ch;
                } else if (ch <= 0x7FF) {
                    *out++ = (char) (0xC0 | (ch >> 6));
                    *out++ = (char) (0x80 | (ch & 0x3F));
                } else if (ch >= 0xD800 && ch <= 0xDBFF && index + 1 < end
                    && values[index + 1] >= 0xDC00 && values[index + 1] <= 0xDFFF) {
                    unsigned int low = values[index + 1];
                    unsigned int code_point = 0x10000 + ((ch - 0xD800) << 10) + (low - 0xDC00);
                    *out++ = (char) (0xF0 | (code_point >> 18));
                    *out++ = (char) (0x80 | ((code_point >> 12) & 0x3F));
                    *out++ = (char) (0x80 | ((code_point >> 6) & 0x3F));
                    *out++ = (char) (0x80 | (code_point & 0x3F));
                    index++;
                } else {
                    *out++ = (char) (0xE0 | (ch >> 12));
                    *out++ = (char) (0x80 | ((ch >> 6) & 0x3F));
                    *out++ = (char) (0x80 | (ch & 0x3F));
                }
                index++;
            }
            return out;
        }

        void* javan_string_from_chars(void* array, int offset, int count) {
            javan_char_array* chars = (javan_char_array*) javan_array_checked(array);
            javan_array_kind_checked((javan_array_header*) chars, JAVAN_ARRAY_KIND_CHAR);
            if (offset < 0 || count < 0 || offset > chars->length || count > chars->length - offset) {
                javan_panic("string index out of bounds");
            }
            int length = javan_utf8_length_from_utf16(chars->values, offset, count);
            void** javan_string_chars_roots[] = {
                (void**) &chars
            };
            javan_root_frame_push(javan_string_chars_roots, 1);
            char* result = javan_string_alloc((unsigned long) length + 1);
            char* out = javan_utf8_write_from_utf16(result, chars->values, offset, count);
            *out = '\\0';
            javan_root_frame_pop(javan_string_chars_roots);
            return result;
        }

        int javan_string_length(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            return (int) strlen(value);
        }

        int javan_string_is_empty(const char* value) {
            return javan_string_length(value) == 0;
        }

        int javan_string_char_at(const char* value, int index) {
            int length = javan_string_length(value);
            if (index < 0 || index >= length) {
                javan_panic("string index out of bounds");
            }
            return (unsigned char) value[index];
        }

        int javan_string_index_of_char(const char* value, int ch) {
            return javan_string_index_of_char_from(value, ch, 0);
        }

        int javan_string_index_of_char_from(const char* value, int ch, int from_index) {
            int length = javan_string_length(value);
            int start = from_index < 0 ? 0 : from_index;
            if (start >= length) {
                return -1;
            }
            for (int index = start; index < length; index++) {
                if (((unsigned char) value[index]) == (unsigned int) (ch & 0xff)) {
                    return index;
                }
            }
            return -1;
        }

        int javan_string_index_of_string(const char* value, const char* needle) {
            return javan_string_index_of_string_from(value, needle, 0);
        }

        int javan_string_index_of_string_from(const char* value, const char* needle, int from_index) {
            if (value == NULL || needle == NULL) {
                javan_panic("null string");
            }
            int length = javan_string_length(value);
            int needle_length = javan_string_length(needle);
            int start = from_index < 0 ? 0 : from_index;
            if (needle_length == 0) {
                return start > length ? length : start;
            }
            if (start > length - needle_length) {
                return -1;
            }
            for (int index = start; index <= length - needle_length; index++) {
                int matched = 1;
                for (int needle_index = 0; needle_index < needle_length; needle_index++) {
                    if (value[index + needle_index] != needle[needle_index]) {
                        matched = 0;
                        break;
                    }
                }
                if (matched) {
                    return index;
                }
            }
            return -1;
        }

        int javan_string_last_index_of_char(const char* value, int ch) {
            int length = javan_string_length(value);
            return javan_string_last_index_of_char_from(value, ch, length - 1);
        }

        int javan_string_last_index_of_char_from(const char* value, int ch, int from_index) {
            int length = javan_string_length(value);
            if (length == 0 || from_index < 0) {
                return -1;
            }
            int start = from_index >= length ? length - 1 : from_index;
            for (int index = start; index >= 0; index--) {
                if (((unsigned char) value[index]) == (unsigned int) (ch & 0xff)) {
                    return index;
                }
            }
            return -1;
        }

        int javan_string_equals(const char* left, const char* right) {
            if (left == NULL || right == NULL) {
                return left == right;
            }
            return strcmp(left, right) == 0;
        }

        int javan_string_contains(const char* left, const char* right) {
            if (left == NULL || right == NULL) {
                javan_panic("null string");
            }
            return strstr(left, right) != NULL;
        }

        int javan_string_starts_with(const char* left, const char* prefix) {
            if (left == NULL || prefix == NULL) {
                javan_panic("null string");
            }
            size_t prefix_length = strlen(prefix);
            return strncmp(left, prefix, prefix_length) == 0;
        }

        int javan_string_ends_with(const char* left, const char* suffix) {
            if (left == NULL || suffix == NULL) {
                javan_panic("null string");
            }
            size_t left_length = strlen(left);
            size_t suffix_length = strlen(suffix);
            if (suffix_length > left_length) {
                return 0;
            }
            return strcmp(left + (left_length - suffix_length), suffix) == 0;
        }

        void* javan_string_replace_char(const char* value, int old_ch, int new_ch) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            void* source_root = (void*) value;
            void** javan_string_replace_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_replace_roots, 1);
            char* result = javan_string_alloc(length + 1);
            unsigned char old_value = (unsigned char) (old_ch & 0xff);
            unsigned char new_value = (unsigned char) (new_ch & 0xff);
            for (unsigned long index = 0; index < length; index++) {
                unsigned char ch = (unsigned char) ((const char*) source_root)[index];
                result[index] = (char) (ch == old_value ? new_value : ch);
            }
            result[length] = '\\0';
            javan_root_frame_pop(javan_string_replace_roots);
            return result;
        }

        void* javan_string_trim(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = (int) strlen(value);
            int begin = 0;
            while (begin < length && ((unsigned char) value[begin]) <= 32) {
                begin++;
            }
            int end = length;
            while (end > begin && ((unsigned char) value[end - 1]) <= 32) {
                end--;
            }
            return javan_string_substring_range(value, begin, end);
        }

        void* javan_string_substring(const char* value, int begin) {
            int length = javan_string_length(value);
            return javan_string_substring_range(value, begin, length);
        }

        void* javan_string_substring_range(const char* value, int begin, int end) {
            int length = javan_string_length(value);
            if (begin < 0 || end < begin || end > length) {
                javan_panic("string index out of bounds");
            }
            int result_length = end - begin;
            void* source_root = (void*) value;
            void** javan_string_substring_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_substring_roots, 1);
            char* result = javan_string_alloc((unsigned long) result_length + 1);
            if (result_length > 0) {
                memcpy(result, ((const char*) source_root) + begin, (unsigned long) result_length);
            }
            result[result_length] = '\\0';
            javan_root_frame_pop(javan_string_substring_roots);
            return result;
        }
        """;

    private static final String SOURCE_COLLECTIONS = """
        static int javan_probably_string_key(void* value) {
            if (value == NULL) {
                return 0;
            }
            const unsigned char* text = (const unsigned char*) value;
            for (int index = 0; index < 4096; index++) {
                unsigned char ch = text[index];
                if (ch == 0) {
                    return 1;
                }
                if (ch < 32 && ch != 9 && ch != 10 && ch != 13) {
                    return 0;
                }
            }
            return 0;
        }

        static int javan_object_equals(void* left, void* right) {
            if (left == right) {
                return 1;
            }
            if (left == NULL || right == NULL) {
                return 0;
            }
            int left_type = javan_registered_type_id(left);
            int right_type = javan_registered_type_id(right);
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return ((javan_boxed_int*) left)->value == ((javan_boxed_int*) right)->value;
            }
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_LONG) {
                return ((javan_boxed_long*) left)->value == ((javan_boxed_long*) right)->value;
            }
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return ((javan_boxed_float*) left)->value == ((javan_boxed_float*) right)->value;
            }
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return ((javan_boxed_double*) left)->value == ((javan_boxed_double*) right)->value;
            }
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return ((javan_boxed_boolean*) left)->value == ((javan_boxed_boolean*) right)->value;
            }
            if (javan_probably_string_key(left) != 0 && javan_probably_string_key(right) != 0) {
                return strcmp((const char*) left, (const char*) right) == 0;
            }
            return 0;
        }

        static javan_object_list* javan_list_new_with_capacity(int capacity, int immutable) {
            if (capacity < 0) {
                javan_panic("negative list capacity");
            }
            javan_object_list* list = (javan_object_list*) javan_alloc(sizeof(javan_object_list));
            list->magic = JAVAN_OBJECT_LIST_MAGIC;
            list->length = 0;
            list->capacity = capacity;
            list->immutable = immutable;
            list->mod_count = 0;
            list->values = NULL;
            javan_update_runtime_allocation_kind((void*) list, JAVAN_RUNTIME_KIND_OBJECT_LIST);
            if (capacity > 0) {
                void** javan_list_owner_roots[] = {
                    (void**) &list
                };
                javan_root_frame_push(javan_list_owner_roots, 1);
                list->values = (void**) javan_alloc((unsigned long) capacity * sizeof(void*));
                javan_update_runtime_allocation_kind((void*) list->values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
                javan_root_frame_pop(javan_list_owner_roots);
            }
            return list;
        }

        static javan_object_list* javan_list_checked(void* value) {
            if (value == NULL) {
                javan_panic("null list");
            }
            javan_object_list* list = (javan_object_list*) value;
            if (list->magic != JAVAN_OBJECT_LIST_MAGIC) {
                javan_panic("unsupported collection object");
            }
            return list;
        }

        static javan_object_iterator* javan_iterator_checked(void* value) {
            if (value == NULL) {
                javan_panic("null iterator");
            }
            javan_object_iterator* iterator = (javan_object_iterator*) value;
            if (iterator->magic != JAVAN_OBJECT_ITERATOR_MAGIC) {
                javan_panic("unsupported iterator object");
            }
            return iterator;
        }

        static void javan_list_mutable_checked(javan_object_list* list) {
            if (list->immutable != 0) {
                javan_panic("unsupported operation on immutable list");
            }
        }

        static void javan_list_bounds_checked(javan_object_list* list, int index) {
            if (index < 0 || index >= list->length) {
                javan_panic("list index out of bounds");
            }
        }

        static void javan_list_ensure_capacity(javan_object_list* list, int required) {
            if (required <= list->capacity) {
                return;
            }
            int next_capacity = list->capacity <= 0 ? 4 : list->capacity * 2;
            while (next_capacity < required) {
                next_capacity *= 2;
            }
            int created_buffer = list->values == NULL;
            void** javan_list_growth_roots[] = {
                (void**) &list
            };
            javan_root_frame_push(javan_list_growth_roots, 1);
            void** next = (void**) javan_realloc_owned_buffer(list->values, (unsigned long) next_capacity * sizeof(void*));
            list->values = next;
            if (created_buffer != 0) {
                javan_update_runtime_allocation_kind((void*) next, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
            } else {
                javan_heap_maybe_validate();
            }
            if (next == NULL) {
                javan_panic("out of memory");
            }
            if (next_capacity > list->capacity) {
                memset(next + list->capacity, 0, (unsigned long) (next_capacity - list->capacity) * sizeof(void*));
            }
            list->capacity = next_capacity;
            javan_root_frame_pop(javan_list_growth_roots);
        }

        static void javan_list_append_raw(javan_object_list* list, void* value) {
            void* value_root = value;
            void** javan_list_append_roots[] = {
                (void**) &list,
                (void**) &value_root
            };
            javan_root_frame_push(javan_list_append_roots, 2);
            javan_list_ensure_capacity(list, list->length + 1);
            list->values[list->length] = value_root;
            list->length++;
            javan_root_frame_pop(javan_list_append_roots);
        }

        void* javan_arraylist_new(void) {
            return javan_list_new_with_capacity(0, 0);
        }

        int javan_arraylist_add(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            javan_list_append_raw(list, element);
            list->mod_count++;
            return 1;
        }

        void javan_arraylist_add_at(void* value, int index, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            if (index < 0 || index > list->length) {
                javan_panic("list index out of bounds");
            }
            void* element_root = element;
            void** javan_list_insert_roots[] = {
                (void**) &list,
                (void**) &element_root
            };
            javan_root_frame_push(javan_list_insert_roots, 2);
            javan_list_ensure_capacity(list, list->length + 1);
            if (index < list->length) {
                memmove(list->values + index + 1, list->values + index, (unsigned long) (list->length - index) * sizeof(void*));
            }
            list->values[index] = element_root;
            list->length++;
            javan_root_frame_pop(javan_list_insert_roots);
            list->mod_count++;
        }

        int javan_arraylist_add_all(void* value, void* collection) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* source = javan_list_checked(collection);
            javan_list_mutable_checked(list);
            int copied = source->length;
            if (copied == 0) {
                return 0;
            }
            void** javan_list_add_all_roots[] = {
                (void**) &list,
                (void**) &source
            };
            javan_root_frame_push(javan_list_add_all_roots, 2);
            javan_list_ensure_capacity(list, list->length + copied);
            for (int index = 0; index < copied; index++) {
                list->values[list->length + index] = source->values[index];
            }
            list->length += copied;
            javan_root_frame_pop(javan_list_add_all_roots);
            list->mod_count++;
            return 1;
        }

        void javan_arraylist_add_first(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            void* element_root = element;
            void** javan_list_add_first_roots[] = {
                (void**) &list,
                (void**) &element_root
            };
            javan_root_frame_push(javan_list_add_first_roots, 2);
            javan_list_ensure_capacity(list, list->length + 1);
            if (list->length > 0) {
                memmove(list->values + 1, list->values, (unsigned long) list->length * sizeof(void*));
            }
            list->values[0] = element_root;
            list->length++;
            javan_root_frame_pop(javan_list_add_first_roots);
            list->mod_count++;
        }

        void* javan_arraylist_set(void* value, int index, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            javan_list_bounds_checked(list, index);
            void* previous = list->values[index];
            list->values[index] = element;
            return previous;
        }

        void* javan_arraylist_remove_last(void* value) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            if (list->length == 0) {
                javan_panic("list is empty");
            }
            list->length--;
            void* previous = list->values[list->length];
            list->values[list->length] = NULL;
            list->mod_count++;
            return previous;
        }

        void* javan_list_of(int count, ...) {
            if (count < 0) {
                javan_panic("negative list size");
            }
            void* values[count > 0 ? count : 1];
            void** roots[count > 0 ? count : 1];
            va_list arguments;
            va_start(arguments, count);
            for (int index = 0; index < count; index++) {
                values[index] = va_arg(arguments, void*);
                roots[index] = &values[index];
            }
            va_end(arguments);
            if (count > 0) {
                javan_root_frame_push(roots, count);
            }
            javan_object_list* list = javan_list_new_with_capacity(count, 1);
            for (int index = 0; index < count; index++) {
                javan_list_append_raw(list, values[index]);
            }
            if (count > 0) {
                javan_root_frame_pop(roots);
            }
            return list;
        }

        void* javan_list_of_array(void* array) {
            javan_array_header* header = javan_array_checked(array);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            javan_object_array* values = (javan_object_array*) header;
            void** javan_list_array_roots[] = {
                (void**) &values
            };
            javan_root_frame_push(javan_list_array_roots, 1);
            javan_object_list* list = javan_list_new_with_capacity(values->length, 1);
            for (int index = 0; index < values->length; index++) {
                javan_list_append_raw(list, values->values[index]);
            }
            javan_root_frame_pop(javan_list_array_roots);
            return list;
        }

        void* javan_list_copy_of(void* collection) {
            javan_object_list* source = javan_list_checked(collection);
            void** javan_list_copy_roots[] = {
                (void**) &source
            };
            javan_root_frame_push(javan_list_copy_roots, 1);
            javan_object_list* list = javan_list_new_with_capacity(source->length, 1);
            for (int index = 0; index < source->length; index++) {
                javan_list_append_raw(list, source->values[index]);
            }
            javan_root_frame_pop(javan_list_copy_roots);
            return list;
        }

        int javan_list_size(void* value) {
            return javan_list_checked(value)->length;
        }

        int javan_list_is_empty(void* value) {
            return javan_list_checked(value)->length == 0;
        }

        int javan_list_contains(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            for (int index = 0; index < list->length; index++) {
                if (javan_object_equals(list->values[index], element) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        void* javan_list_get(void* value, int index) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_bounds_checked(list, index);
            return list->values[index];
        }

        void* javan_list_get_first(void* value) {
            return javan_list_get(value, 0);
        }

        void* javan_list_get_last(void* value) {
            javan_object_list* list = javan_list_checked(value);
            if (list->length == 0) {
                javan_panic("list is empty");
            }
            return list->values[list->length - 1];
        }

        void* javan_list_iterator(void* value) {
            javan_object_list* list = javan_list_checked(value);
            void** javan_list_iterator_roots[] = {
                (void**) &list
            };
            javan_root_frame_push(javan_list_iterator_roots, 1);
            javan_object_iterator* iterator = (javan_object_iterator*) javan_alloc(sizeof(javan_object_iterator));
            iterator->magic = JAVAN_OBJECT_ITERATOR_MAGIC;
            iterator->index = 0;
            iterator->expected_mod_count = list->mod_count;
            iterator->reserved = 0;
            iterator->list = list;
            javan_update_runtime_allocation_kind((void*) iterator, JAVAN_RUNTIME_KIND_OBJECT_ITERATOR);
            javan_root_frame_pop(javan_list_iterator_roots);
            return iterator;
        }

        int javan_iterator_has_next(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            return iterator->index < iterator->list->length;
        }

        void* javan_iterator_next(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            if (iterator->expected_mod_count != iterator->list->mod_count) {
                javan_panic("concurrent list modification");
            }
            if (iterator->index >= iterator->list->length) {
                javan_panic("iterator exhausted");
            }
            void* result = iterator->list->values[iterator->index];
            iterator->index++;
            return result;
        }

        static javan_object_map* javan_map_new_with_capacity(int capacity, int immutable) {
            if (capacity < 0) {
                javan_panic("negative map capacity");
            }
            javan_object_map* map = (javan_object_map*) javan_alloc(sizeof(javan_object_map));
            map->magic = JAVAN_OBJECT_MAP_MAGIC;
            map->length = 0;
            map->capacity = capacity;
            map->immutable = immutable;
            map->mod_count = 0;
            map->keys = NULL;
            map->values = NULL;
            javan_update_runtime_allocation_kind((void*) map, JAVAN_RUNTIME_KIND_OBJECT_MAP);
            if (capacity > 0) {
                void** next_keys = NULL;
                void** next_values = NULL;
                void** javan_map_owner_roots[] = {
                    (void**) &map,
                    (void**) &next_keys,
                    (void**) &next_values
                };
                javan_root_frame_push(javan_map_owner_roots, 3);
                next_keys = (void**) javan_alloc((unsigned long) capacity * sizeof(void*));
                javan_update_runtime_allocation_kind((void*) next_keys, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
                next_values = (void**) javan_alloc((unsigned long) capacity * sizeof(void*));
                javan_update_runtime_allocation_kind((void*) next_values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
                map->keys = next_keys;
                map->values = next_values;
                javan_root_frame_pop(javan_map_owner_roots);
            }
            return map;
        }

        static javan_object_map* javan_map_checked(void* value) {
            if (value == NULL) {
                javan_panic("null map");
            }
            javan_object_map* map = (javan_object_map*) value;
            if (map->magic != JAVAN_OBJECT_MAP_MAGIC) {
                javan_panic("unsupported map object");
            }
            return map;
        }

        static void javan_map_mutable_checked(javan_object_map* map) {
            if (map->immutable != 0) {
                javan_panic("unsupported operation on immutable map");
            }
        }

        static void javan_map_ensure_capacity(javan_object_map* map, int required) {
            if (required <= map->capacity) {
                return;
            }
            int next_capacity = map->capacity <= 0 ? 8 : map->capacity * 2;
            while (next_capacity < required) {
                next_capacity *= 2;
            }
            int created_keys = map->keys == NULL;
            int created_values = map->values == NULL;
            void** old_keys = map->keys;
            void** old_values = map->values;
            void** next_keys = old_keys;
            void** next_values = old_values;
            void** javan_map_growth_roots[] = {
                (void**) &map,
                (void**) &next_keys,
                (void**) &next_values
            };
            javan_root_frame_push(javan_map_growth_roots, 3);
            next_keys = (void**) javan_realloc_owned_buffer(old_keys, (unsigned long) next_capacity * sizeof(void*));
            map->keys = next_keys;
            if (created_keys != 0) {
                javan_update_runtime_allocation_kind((void*) next_keys, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
            } else {
                javan_heap_maybe_validate();
            }
            next_values = (void**) javan_realloc_owned_buffer(old_values, (unsigned long) next_capacity * sizeof(void*));
            map->values = next_values;
            if (created_values != 0) {
                javan_update_runtime_allocation_kind((void*) next_values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
            } else {
                javan_heap_maybe_validate();
            }
            if (next_keys == NULL || next_values == NULL) {
                javan_panic("out of memory");
            }
            if (next_capacity > map->capacity) {
                memset(next_keys + map->capacity, 0, (unsigned long) (next_capacity - map->capacity) * sizeof(void*));
                memset(next_values + map->capacity, 0, (unsigned long) (next_capacity - map->capacity) * sizeof(void*));
            }
            map->capacity = next_capacity;
            javan_root_frame_pop(javan_map_growth_roots);
        }

        static int javan_map_key_equals(void* left, void* right) {
            return javan_object_equals(left, right);
        }

        static int javan_map_find(javan_object_map* map, void* key) {
            for (int index = 0; index < map->length; index++) {
                if (javan_map_key_equals(map->keys[index], key) != 0) {
                    return index;
                }
            }
            return -1;
        }

        void* javan_hashmap_new(void) {
            return javan_map_new_with_capacity(0, 0);
        }

        void* javan_map_copy_of(void* value) {
            javan_object_map* source = javan_map_checked(value);
            void** javan_map_copy_roots[] = {
                (void**) &source
            };
            javan_root_frame_push(javan_map_copy_roots, 1);
            javan_object_map* result = javan_map_new_with_capacity(source->length, 1);
            for (int index = 0; index < source->length; index++) {
                result->keys[index] = source->keys[index];
                result->values[index] = source->values[index];
            }
            result->length = source->length;
            javan_root_frame_pop(javan_map_copy_roots);
            return result;
        }

        void* javan_map_get(void* value, void* key) {
            javan_object_map* map = javan_map_checked(value);
            int index = javan_map_find(map, key);
            return index < 0 ? NULL : map->values[index];
        }

        void* javan_map_get_or_default(void* value, void* key, void* fallback) {
            javan_object_map* map = javan_map_checked(value);
            int index = javan_map_find(map, key);
            return index < 0 ? fallback : map->values[index];
        }

        void* javan_map_put(void* value, void* key, void* element) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            int index = javan_map_find(map, key);
            if (index >= 0) {
                void* previous = map->values[index];
                map->values[index] = element;
                return previous;
            }
            void* key_root = key;
            void* element_root = element;
            void** javan_map_put_roots[] = {
                (void**) &map,
                (void**) &key_root,
                (void**) &element_root
            };
            javan_root_frame_push(javan_map_put_roots, 3);
            javan_map_ensure_capacity(map, map->length + 1);
            map->keys[map->length] = key_root;
            map->values[map->length] = element_root;
            map->length++;
            javan_root_frame_pop(javan_map_put_roots);
            map->mod_count++;
            return NULL;
        }

        void* javan_map_put_if_absent(void* value, void* key, void* element) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            int index = javan_map_find(map, key);
            if (index >= 0) {
                return map->values[index];
            }
            void* key_root = key;
            void* element_root = element;
            void** javan_map_put_absent_roots[] = {
                (void**) &map,
                (void**) &key_root,
                (void**) &element_root
            };
            javan_root_frame_push(javan_map_put_absent_roots, 3);
            javan_map_ensure_capacity(map, map->length + 1);
            map->keys[map->length] = key_root;
            map->values[map->length] = element_root;
            map->length++;
            javan_root_frame_pop(javan_map_put_absent_roots);
            map->mod_count++;
            return NULL;
        }

        int javan_map_contains_key(void* value, void* key) {
            return javan_map_find(javan_map_checked(value), key) >= 0;
        }

        int javan_map_size(void* value) {
            return javan_map_checked(value)->length;
        }

        int javan_map_is_empty(void* value) {
            return javan_map_checked(value)->length == 0;
        }

        void* javan_map_values(void* value) {
            javan_object_map* map = javan_map_checked(value);
            void** javan_map_values_roots[] = {
                (void**) &map
            };
            javan_root_frame_push(javan_map_values_roots, 1);
            javan_object_list* list = javan_list_new_with_capacity(map->length, 1);
            for (int index = 0; index < map->length; index++) {
                javan_list_append_raw(list, map->values[index]);
            }
            javan_root_frame_pop(javan_map_values_roots);
            return list;
        }

        static void* javan_string_copy(const char* value) {
            const char* source = value == NULL ? "null" : value;
            unsigned long length = strlen(source);
            void* source_root = (void*) source;
            void** javan_string_copy_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_copy_roots, 1);
            char* result = javan_string_alloc(length + 1);
            memcpy(result, (const char*) source_root, length + 1);
            javan_root_frame_pop(javan_string_copy_roots);
            return result;
        }

        void* javan_string_from(const char* value) {
            if (value == NULL) {
                return NULL;
            }
            return javan_string_copy(value);
        }

        static char* javan_file_to_string(FILE* file) {
            if (file == NULL) {
                return (char*) javan_string_copy("");
            }
            fflush(file);
            if (fseek(file, 0, SEEK_END) != 0) {
                javan_panic("process output seek failed");
            }
            long length = ftell(file);
            if (length < 0) {
                javan_panic("process output length failed");
            }
            if (fseek(file, 0, SEEK_SET) != 0) {
                javan_panic("process output rewind failed");
            }
            char* result = javan_string_alloc((unsigned long) length + 1);
            unsigned long read = fread(result, 1, (unsigned long) length, file);
            result[read] = '\\0';
            return result;
        }

        static javan_process_result* javan_process_result_new(int exit_code, const char* stdout_value, const char* stderr_value) {
            void* stdout_root = (void*) stdout_value;
            void* stderr_root = (void*) stderr_value;
            void* result_root = NULL;
            void** javan_process_result_roots[] = {
                (void**) &stdout_root,
                (void**) &stderr_root,
                (void**) &result_root
            };
            javan_root_frame_push(javan_process_result_roots, 3);
            javan_process_result* result = (javan_process_result*) javan_alloc(sizeof(javan_process_result));
            result_root = (void*) result;
            javan_update_runtime_allocation_kind((void*) result, JAVAN_RUNTIME_KIND_PROCESS_RESULT);
            result->exit_code = exit_code;
            result->stdout_value = (char*) javan_string_copy((const char*) stdout_root);
            result->stderr_value = (char*) javan_string_copy((const char*) stderr_root);
            javan_root_frame_pop(javan_process_result_roots);
            return result;
        }

        void* javan_process_run(void* cwd, void* command_value, long long timeout_millis) {
            javan_object_list* command = javan_list_checked(command_value);
            if (command->length <= 0) {
                return javan_process_result_new(127, "", "empty command");
            }
            void* cwd_root = cwd;
            void* command_root = command_value;
            void** javan_process_command_roots[] = {
                (void**) &cwd_root,
                (void**) &command_root
            };
            javan_root_frame_push(javan_process_command_roots, 2);
            char** argv = (char**) javan_alloc((unsigned long) (command->length + 1) * sizeof(char*));
            for (int index = 0; index < command->length; index++) {
                argv[index] = (char*) command->values[index];
                if (argv[index] == NULL) {
                    argv[index] = "";
                }
            }
            argv[command->length] = NULL;
            javan_root_frame_pop(javan_process_command_roots);

            FILE* stdout_file = tmpfile();
            FILE* stderr_file = tmpfile();
            if (stdout_file == NULL || stderr_file == NULL) {
                if (stdout_file != NULL) {
                    fclose(stdout_file);
                }
                if (stderr_file != NULL) {
                    fclose(stderr_file);
                }
                javan_free(argv);
                return javan_process_result_new(127, "", "process output capture failed");
            }
            javan_native_resource_frame stdout_resource;
            javan_native_resource_frame stderr_resource;
            javan_native_resource_push(&stdout_resource, stdout_file, javan_native_file_cleanup);
            javan_native_resource_push(&stderr_resource, stderr_file, javan_native_file_cleanup);
            pid_t child = fork();
            if (child < 0) {
                javan_native_resource_pop(&stderr_resource);
                fclose(stderr_file);
                javan_native_resource_pop(&stdout_resource);
                fclose(stdout_file);
                javan_free(argv);
                return javan_process_result_new(127, "", "process fork failed");
            }
            if (child == 0) {
                if (cwd != NULL && chdir((const char*) cwd) != 0) {
                    _exit(127);
                }
                dup2(fileno(stdout_file), STDOUT_FILENO);
                dup2(fileno(stderr_file), STDERR_FILENO);
                execvp(argv[0], argv);
                _exit(127);
            }

            int status = 0;
            int completed = 0;
            long long started = javan_system_current_time_millis();
            long long timeout = timeout_millis <= 0 ? 300000LL : timeout_millis;
            while (completed == 0) {
                pid_t waited = waitpid(child, &status, WNOHANG);
                if (waited == child) {
                    completed = 1;
                    break;
                }
                if (waited < 0) {
                    status = 127 << 8;
                    completed = 1;
                    break;
                }
                if (javan_system_current_time_millis() - started >= timeout) {
                    kill(child, SIGKILL);
                    waitpid(child, &status, 0);
                    char* stdout_text = javan_file_to_string(stdout_file);
                    javan_native_resource_pop(&stderr_resource);
                    fclose(stderr_file);
                    javan_native_resource_pop(&stdout_resource);
                    fclose(stdout_file);
                    javan_free(argv);
                    javan_process_result* result = javan_process_result_new(124, stdout_text, "Timed out");
                    javan_free(stdout_text);
                    return result;
                }
                usleep(10000);
            }

            int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 127;
            char* stdout_text = javan_file_to_string(stdout_file);
            void** javan_process_stdout_roots[] = {
                (void**) &stdout_text
            };
            javan_root_frame_push(javan_process_stdout_roots, 1);
            char* stderr_text = javan_file_to_string(stderr_file);
            javan_root_frame_pop(javan_process_stdout_roots);
            javan_native_resource_pop(&stderr_resource);
            fclose(stderr_file);
            javan_native_resource_pop(&stdout_resource);
            fclose(stdout_file);
            javan_free(argv);
            javan_process_result* result = javan_process_result_new(exit_code, stdout_text, stderr_text);
            javan_free(stdout_text);
            javan_free(stderr_text);
            return result;
        }

        int javan_process_result_exit_code(void* value) {
            if (value == NULL) {
                javan_panic("null process result");
            }
            return ((javan_process_result*) value)->exit_code;
        }

        void* javan_process_result_stdout(void* value) {
            if (value == NULL) {
                javan_panic("null process result");
            }
            return ((javan_process_result*) value)->stdout_value;
        }

        void* javan_process_result_stderr(void* value) {
            if (value == NULL) {
                javan_panic("null process result");
            }
            return ((javan_process_result*) value)->stderr_value;
        }
        """;

    private static final String SOURCE_TAIL = """
        static javan_string_builder* javan_stringbuilder_checked(void* value) {
            if (value == NULL) {
                javan_panic("null string builder");
            }
            javan_string_builder* builder = (javan_string_builder*) value;
            if (builder->magic != JAVAN_STRING_BUILDER_MAGIC) {
                javan_panic("unsupported string builder object");
            }
            return builder;
        }

        static void javan_stringbuilder_ensure_capacity(javan_string_builder* builder, int required) {
            if (required < 0) {
                javan_panic("string builder length overflow");
            }
            if (required == INT_MAX) {
                javan_panic("string builder length overflow");
            }
            unsigned long required_size = (unsigned long) required + 1UL;
            if (required_size <= (unsigned long) builder->capacity) {
                return;
            }
            int next_capacity = 32;
            if (builder->capacity > 0) {
                if (builder->capacity > INT_MAX / 2) {
                    javan_panic("string builder length overflow");
                }
                next_capacity = builder->capacity * 2;
            }
            while ((unsigned long) next_capacity < required_size) {
                if (next_capacity > INT_MAX / 2) {
                    javan_panic("string builder length overflow");
                }
                next_capacity *= 2;
            }
            int old_capacity = builder->capacity;
            int created_buffer = builder->values == NULL;
            void** javan_builder_growth_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_growth_roots, 1);
            char* next = (char*) javan_realloc_owned_buffer(builder->values, (unsigned long) next_capacity);
            builder->values = next;
            builder->capacity = next_capacity;
            if (created_buffer != 0) {
                javan_update_runtime_allocation_kind((void*) next, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
            } else {
                javan_heap_maybe_validate();
            }
            if (next == NULL) {
                javan_panic("out of memory");
            }
            if (next_capacity > old_capacity) {
                memset(next + old_capacity, 0, (unsigned long) (next_capacity - old_capacity));
            }
            javan_root_frame_pop(javan_builder_growth_roots);
        }

        static void javan_stringbuilder_append_bytes(javan_string_builder* builder, const char* value) {
            const char* source = value == NULL ? "null" : value;
            unsigned long length = strlen(source);
            if (length > (unsigned long) INT_MAX || builder->length > INT_MAX - (int) length) {
                javan_panic("string builder length overflow");
            }
            void* source_root = (void*) source;
            void** javan_builder_append_roots[] = {
                (void**) &builder,
                (void**) &source_root
            };
            javan_root_frame_push(javan_builder_append_roots, 2);
            javan_stringbuilder_ensure_capacity(builder, builder->length + (int) length);
            memcpy(builder->values + builder->length, (const char*) source_root, length);
            builder->length += (int) length;
            builder->values[builder->length] = '\\0';
            javan_root_frame_pop(javan_builder_append_roots);
        }

        void* javan_stringbuilder_new(void) {
            javan_string_builder* builder = (javan_string_builder*) javan_alloc(sizeof(javan_string_builder));
            builder->magic = JAVAN_STRING_BUILDER_MAGIC;
            builder->length = 0;
            builder->capacity = 0;
            builder->reserved = 0;
            builder->values = NULL;
            javan_update_runtime_allocation_kind((void*) builder, JAVAN_RUNTIME_KIND_STRING_BUILDER);
            void** javan_builder_owner_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_owner_roots, 1);
            javan_stringbuilder_ensure_capacity(builder, 0);
            javan_root_frame_pop(javan_builder_owner_roots);
            builder->values[0] = '\\0';
            return builder;
        }

        void* javan_stringbuilder_append_string(void* builder_value, void* value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_append_bytes(builder, (const char*) value);
            return builder;
        }

        void* javan_stringbuilder_append_object(void* builder_value, void* value) {
            return javan_stringbuilder_append_string(builder_value, value);
        }

        void* javan_stringbuilder_append_boolean(void* builder_value, int value) {
            javan_stringbuilder_append_bytes(javan_stringbuilder_checked(builder_value), value == 0 ? "false" : "true");
            return builder_value;
        }

        void* javan_stringbuilder_append_char(void* builder_value, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            char buffer[2];
            buffer[0] = (char) value;
            buffer[1] = '\\0';
            javan_stringbuilder_append_bytes(builder, buffer);
            return builder;
        }

        void* javan_stringbuilder_append_int(void* builder_value, int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            javan_stringbuilder_append_bytes(javan_stringbuilder_checked(builder_value), buffer);
            return builder_value;
        }

        void* javan_stringbuilder_append_long(void* builder_value, long long value) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            javan_stringbuilder_append_bytes(javan_stringbuilder_checked(builder_value), buffer);
            return builder_value;
        }

        void* javan_stringbuilder_to_string(void* builder_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void** javan_builder_to_string_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_to_string_roots, 1);
            void* result = javan_string_copy(builder->values);
            javan_root_frame_pop(javan_builder_to_string_roots);
            return result;
        }

        int javan_stringbuilder_length(void* builder_value) {
            return javan_stringbuilder_checked(builder_value)->length;
        }

        int javan_stringbuilder_is_empty(void* builder_value) {
            return javan_stringbuilder_checked(builder_value)->length == 0;
        }

        void javan_stringbuilder_set_length(void* builder_value, int length) {
            if (length < 0) {
                javan_panic("negative string builder length");
            }
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void** javan_builder_set_length_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_set_length_roots, 1);
            javan_stringbuilder_ensure_capacity(builder, length);
            if (length > builder->length) {
                memset(builder->values + builder->length, 0, (unsigned long) (length - builder->length));
            }
            builder->length = length;
            builder->values[length] = '\\0';
            javan_root_frame_pop(javan_builder_set_length_roots);
        }

        static const char* javan_path_checked(void* value) {
            if (value == NULL) {
                javan_panic("null path");
            }
            return (const char*) value;
        }

        static void javan_empty_options_checked(void* options) {
            javan_array_header* header = javan_array_checked(options);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            if (header->length != 0) {
                javan_panic("unsupported filesystem options");
            }
        }

        static int javan_link_options_no_follow(void* options) {
            javan_array_header* header = javan_array_checked(options);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            javan_object_array* values = (javan_object_array*) header;
            int no_follow = 0;
            for (int index = 0; index < values->length; index++) {
                const char* option = (const char*) values->values[index];
                if (option == NULL || strcmp(option, "NOFOLLOW_LINKS") != 0) {
                    javan_panic("unsupported link option");
                }
                no_follow = 1;
            }
            return no_follow;
        }

        static int javan_stat_path(const char* path, int no_follow, struct stat* info) {
        #if defined(_WIN32)
            (void) no_follow;
            return stat(path, info);
        #else
            if (no_follow) {
                return lstat(path, info);
            }
            return stat(path, info);
        #endif
        }

        static void javan_copy_options_checked(void* options) {
            javan_array_header* header = javan_array_checked(options);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            javan_object_array* values = (javan_object_array*) header;
            for (int index = 0; index < values->length; index++) {
                const char* option = (const char*) values->values[index];
                if (option == NULL || strcmp(option, "REPLACE_EXISTING") != 0) {
                    javan_panic("unsupported file copy option");
                }
            }
        }

        static unsigned long javan_path_joined_length(const char* first, javan_object_array* more) {
            unsigned long length = strlen(first);
            char last = length == 0 ? '\\0' : first[length - 1];
            for (int index = 0; index < more->length; index++) {
                const char* part = javan_path_checked(more->values[index]);
                unsigned long part_length = strlen(part);
                if (length > 0 && last != '/' && part_length > 0 && part[0] != '/') {
                    length++;
                }
                length += part_length;
                if (part_length > 0) {
                    last = part[part_length - 1];
                }
            }
            return length;
        }

        void* javan_path_of(void* first_value, void* more_value) {
            const char* first = javan_path_checked(first_value);
            javan_array_header* header = javan_array_checked(more_value);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            javan_object_array* more = (javan_object_array*) header;
            unsigned long length = javan_path_joined_length(first, more);
            void* first_root = first_value;
            void* more_root = more_value;
            void** javan_path_of_roots[] = {
                (void**) &first_root,
                (void**) &more_root
            };
            javan_root_frame_push(javan_path_of_roots, 2);
            first = javan_path_checked(first_root);
            more = (javan_object_array*) javan_array_checked(more_root);
            char* result = javan_string_alloc(length + 1);
            result[0] = '\\0';
            strcat(result, first);
            for (int index = 0; index < more->length; index++) {
                const char* part = javan_path_checked(more->values[index]);
                unsigned long current = strlen(result);
                if (current > 0 && result[current - 1] != '/' && part[0] != '\\0' && part[0] != '/') {
                    strcat(result, "/");
                }
                strcat(result, part);
            }
            javan_root_frame_pop(javan_path_of_roots);
            return result;
        }

        void* javan_path_resolve(void* path_value, void* child_value) {
            const char* path = javan_path_checked(path_value);
            const char* child = javan_path_checked(child_value);
            if (child[0] == '/') {
                return javan_string_copy(child);
            }
            if (path[0] == '\\0') {
                return javan_string_copy(child);
            }
            unsigned long path_length = strlen(path);
            unsigned long child_length = strlen(child);
            int slash = path[path_length - 1] == '/' || child[0] == '\\0' ? 0 : 1;
            void* path_root = path_value;
            void* child_root = child_value;
            void** javan_path_resolve_roots[] = {
                (void**) &path_root,
                (void**) &child_root
            };
            javan_root_frame_push(javan_path_resolve_roots, 2);
            path = javan_path_checked(path_root);
            child = javan_path_checked(child_root);
            char* result = javan_string_alloc(path_length + (unsigned long) slash + child_length + 1);
            memcpy(result, path, path_length);
            unsigned long offset = path_length;
            if (slash != 0) {
                result[offset] = '/';
                offset++;
            }
            memcpy(result + offset, child, child_length + 1);
            javan_root_frame_pop(javan_path_resolve_roots);
            return result;
        }

        int javan_path_is_absolute(void* path_value) {
            const char* path = javan_path_checked(path_value);
            return path[0] == '/';
        }

        void* javan_path_to_absolute(void* path_value) {
            const char* path = javan_path_checked(path_value);
            if (path[0] == '/') {
                return javan_string_copy(path);
            }
            char cwd[4096];
            if (getcwd(cwd, sizeof(cwd)) == NULL) {
                javan_panic("toAbsolutePath failed");
            }
            return javan_path_resolve(cwd, (void*) path);
        }

        void* javan_path_normalize(void* path_value) {
            const char* path = javan_path_checked(path_value);
            unsigned long length = strlen(path);
            void* path_root = path_value;
            void** javan_path_normalize_roots[] = {
                (void**) &path_root
            };
            javan_root_frame_push(javan_path_normalize_roots, 1);
            path = javan_path_checked(path_root);
            const char** starts = malloc((length + 1) * sizeof(const char*));
            unsigned long* lengths = malloc((length + 1) * sizeof(unsigned long));
            if (starts == NULL || lengths == NULL) {
                free(starts);
                free(lengths);
                javan_panic("Path.normalize allocation failed");
            }
            int absolute = length > 0 && path[0] == '/';
            unsigned long count = 0;
            unsigned long index = 0;
            while (index < length) {
                while (index < length && path[index] == '/') {
                    index++;
                }
                unsigned long start = index;
                while (index < length && path[index] != '/') {
                    index++;
                }
                unsigned long segment_length = index - start;
                if (segment_length == 0) {
                    continue;
                }
                if (segment_length == 1 && path[start] == '.') {
                    continue;
                }
                if (segment_length == 2 && path[start] == '.' && path[start + 1] == '.') {
                    if (count > 0 && !(lengths[count - 1] == 2 && starts[count - 1][0] == '.' && starts[count - 1][1] == '.')) {
                        count--;
                    } else if (absolute == 0) {
                        starts[count] = path + start;
                        lengths[count] = segment_length;
                        count++;
                    }
                    continue;
                }
                starts[count] = path + start;
                lengths[count] = segment_length;
                count++;
            }
            unsigned long out_length = 0;
            if (absolute != 0 && count == 0) {
                out_length = 1;
            } else if (absolute != 0 && count > 0) {
                out_length = 1;
                for (unsigned long part = 0; part < count; part++) {
                    out_length += lengths[part];
                    if (part + 1 < count) {
                        out_length++;
                    }
                }
            } else {
                out_length = 0;
                for (unsigned long part = 0; part < count; part++) {
                    out_length += lengths[part];
                    if (part + 1 < count) {
                        out_length++;
                    }
                }
            }
            char* result = javan_string_alloc(out_length + 1);
            unsigned long out = 0;
            if (absolute != 0) {
                result[out] = '/';
                out++;
            }
            for (unsigned long part = 0; part < count; part++) {
                if (out > 0 && result[out - 1] != '/') {
                    result[out] = '/';
                    out++;
                }
                memcpy(result + out, starts[part], lengths[part]);
                out += lengths[part];
            }
            result[out] = '\\0';
            free(starts);
            free(lengths);
            javan_root_frame_pop(javan_path_normalize_roots);
            return result;
        }

        void* javan_path_get_parent(void* path_value) {
            const char* path = javan_path_checked(path_value);
            unsigned long length = strlen(path);
            while (length > 1 && path[length - 1] == '/') {
                length--;
            }
            unsigned long slash = length;
            while (slash > 0 && path[slash - 1] != '/') {
                slash--;
            }
            if (slash == 0) {
                return NULL;
            }
            if (slash == 1) {
                return javan_string_copy("/");
            }
            void* path_root = path_value;
            void** javan_path_parent_roots[] = {
                (void**) &path_root
            };
            javan_root_frame_push(javan_path_parent_roots, 1);
            path = javan_path_checked(path_root);
            char* result = javan_string_alloc(slash);
            memcpy(result, path, slash - 1);
            result[slash - 1] = '\\0';
            javan_root_frame_pop(javan_path_parent_roots);
            return result;
        }

        void* javan_path_get_file_name(void* path_value) {
            const char* path = javan_path_checked(path_value);
            unsigned long length = strlen(path);
            while (length > 1 && path[length - 1] == '/') {
                length--;
            }
            unsigned long start = length;
            while (start > 0 && path[start - 1] != '/') {
                start--;
            }
            unsigned long size = length - start;
            void* path_root = path_value;
            void** javan_path_file_name_roots[] = {
                (void**) &path_root
            };
            javan_root_frame_push(javan_path_file_name_roots, 1);
            path = javan_path_checked(path_root);
            char* result = javan_string_alloc(size + 1);
            memcpy(result, path + start, size);
            result[size] = '\\0';
            javan_root_frame_pop(javan_path_file_name_roots);
            return result;
        }

        int javan_path_equals(void* path_value, void* other_value) {
            if (path_value == NULL || other_value == NULL) {
                return path_value == other_value;
            }
            return strcmp(javan_path_checked(path_value), javan_path_checked(other_value)) == 0;
        }

        int javan_path_starts_with(void* path_value, void* prefix_value) {
            const char* path = javan_path_checked(path_value);
            const char* prefix = javan_path_checked(prefix_value);
            unsigned long prefix_length = strlen(prefix);
            if (strncmp(path, prefix, prefix_length) != 0) {
                return 0;
            }
            return path[prefix_length] == '\\0' || path[prefix_length] == '/' || (prefix_length > 0 && prefix[prefix_length - 1] == '/');
        }

        void* javan_path_relativize(void* path_value, void* child_value) {
            const char* path = javan_path_checked(path_value);
            const char* child = javan_path_checked(child_value);
            unsigned long path_length = strlen(path);
            if (strncmp(child, path, path_length) == 0) {
                if (child[path_length] == '\\0') {
                    return javan_string_copy("");
                }
                if (child[path_length] == '/') {
                    void* child_root = child_value;
                    void** javan_path_relativize_roots[] = {
                        (void**) &child_root
                    };
                    javan_root_frame_push(javan_path_relativize_roots, 1);
                    child = javan_path_checked(child_root);
                    const char* suffix = child + path_length + 1;
                    unsigned long suffix_length = strlen(suffix);
                    char* result = javan_string_alloc(suffix_length + 1);
                    memcpy(result, suffix, suffix_length + 1);
                    javan_root_frame_pop(javan_path_relativize_roots);
                    return result;
                }
            }
            return javan_string_copy(child);
        }

        int javan_path_get_name_count(void* path_value) {
            const char* path = javan_path_checked(path_value);
            int count = 0;
            int in_name = 0;
            for (const char* cursor = path; *cursor != '\\0'; cursor++) {
                if (*cursor == '/') {
                    in_name = 0;
                } else if (in_name == 0) {
                    count++;
                    in_name = 1;
                }
            }
            return count;
        }

        void* javan_path_get_name(void* path_value, int index) {
            if (index < 0) {
                javan_panic("path name index out of bounds");
            }
            const char* path = javan_path_checked(path_value);
            int current = -1;
            const char* start = NULL;
            for (const char* cursor = path; ; cursor++) {
                if (*cursor == '/' || *cursor == '\\0') {
                    if (start != NULL) {
                        if (current == index) {
                            unsigned long size = (unsigned long) (cursor - start);
                            unsigned long offset = (unsigned long) (start - path);
                            void* path_root = path_value;
                            void** javan_path_name_roots[] = {
                                (void**) &path_root
                            };
                            javan_root_frame_push(javan_path_name_roots, 1);
                            path = javan_path_checked(path_root);
                            char* result = javan_string_alloc(size + 1);
                            memcpy(result, path + offset, size);
                            result[size] = '\\0';
                            javan_root_frame_pop(javan_path_name_roots);
                            return result;
                        }
                        start = NULL;
                    }
                    if (*cursor == '\\0') {
                        break;
                    }
                } else if (start == NULL) {
                    current++;
                    start = cursor;
                }
            }
            javan_panic("path name index out of bounds");
            return NULL;
        }

        static javan_inet_address* javan_inet_address_checked(void* value) {
            if (value == NULL) {
                javan_panic("null inet address");
            }
            javan_inet_address* address = (javan_inet_address*) value;
            if (address->magic != JAVAN_INET_ADDRESS_MAGIC) {
                javan_panic("unsupported inet address object");
            }
            return address;
        }

        static javan_inet_socket_address* javan_inet_socket_address_checked(void* value) {
            if (value == NULL) {
                javan_panic("null inet socket address");
            }
            javan_inet_socket_address* address = (javan_inet_socket_address*) value;
            if (address->magic != JAVAN_INET_SOCKET_ADDRESS_MAGIC) {
                javan_panic("unsupported inet socket address object");
            }
            return address;
        }

        static void* javan_inet_address_new(const char* host_address, const char* host_name) {
            const char* address_value = host_address == NULL ? "0.0.0.0" : host_address;
            const char* name_value = host_name == NULL ? address_value : host_name;
            void* address_root = (void*) address_value;
            void* name_root = (void*) name_value;
            void** javan_inet_address_roots[] = {
                (void**) &address_root,
                (void**) &name_root
            };
            javan_root_frame_push(javan_inet_address_roots, 2);
            javan_inet_address* address = (javan_inet_address*) javan_alloc(sizeof(javan_inet_address));
            void* object_root = (void*) address;
            void** javan_inet_address_object_roots[] = {
                (void**) &address_root,
                (void**) &name_root,
                (void**) &object_root
            };
            javan_root_frame_push(javan_inet_address_object_roots, 3);
            address->magic = JAVAN_INET_ADDRESS_MAGIC;
            address->reserved0 = 0;
            address->reserved1 = 0;
            address->reserved2 = 0;
            address->host_address = (char*) javan_string_copy((const char*) address_root);
            address->host_name = (char*) javan_string_copy((const char*) name_root);
            javan_update_runtime_allocation_kind((void*) address, JAVAN_RUNTIME_KIND_INET_ADDRESS);
            javan_root_frame_pop(javan_inet_address_object_roots);
            javan_root_frame_pop(javan_inet_address_roots);
            return address;
        }

        void* javan_inet_address_loopback(void) {
            return javan_inet_address_new("127.0.0.1", "localhost");
        }

        void* javan_inet_address_get_host_address(void* value) {
            return javan_inet_address_checked(value)->host_address;
        }

        void* javan_inet_address_get_host_name(void* value) {
            return javan_inet_address_checked(value)->host_name;
        }

        void* javan_inet_address_get_canonical_host_name(void* value) {
            return javan_inet_address_checked(value)->host_name;
        }

        static void* javan_inet_socket_address_new(void* address_value, int port, int unresolved) {
            if (port < 0) {
                javan_panic("negative port");
            }
            void* address_root = address_value;
            void** javan_inet_socket_address_roots[] = {
                (void**) &address_root
            };
            javan_root_frame_push(javan_inet_socket_address_roots, 1);
            javan_inet_socket_address* socket_address = (javan_inet_socket_address*) javan_alloc(sizeof(javan_inet_socket_address));
            socket_address->magic = JAVAN_INET_SOCKET_ADDRESS_MAGIC;
            socket_address->port = port;
            socket_address->reserved0 = unresolved;
            socket_address->reserved1 = 0;
            socket_address->address = (javan_inet_address*) address_root;
            javan_update_runtime_allocation_kind((void*) socket_address, JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS);
            javan_root_frame_pop(javan_inet_socket_address_roots);
            return socket_address;
        }

        void* javan_inet_socket_address_from_host(void* host, int port) {
            const char* host_value = host == NULL ? "0.0.0.0" : (const char*) host;
            void* address = javan_inet_address_new(host_value, host_value);
            void* address_root = address;
            void** javan_inet_socket_host_roots[] = {
                (void**) &address_root
            };
            javan_root_frame_push(javan_inet_socket_host_roots, 1);
            void* result = javan_inet_socket_address_new(address_root, port, 1);
            javan_root_frame_pop(javan_inet_socket_host_roots);
            return result;
        }

        void* javan_inet_socket_address_from_address(void* address, int port) {
            javan_inet_address_checked(address);
            return javan_inet_socket_address_new(address, port, 0);
        }

        int javan_inet_socket_address_get_port(void* value) {
            return javan_inet_socket_address_checked(value)->port;
        }

        void* javan_inet_socket_address_get_host_string(void* value) {
            return javan_inet_socket_address_checked(value)->address->host_name;
        }

        void* javan_inet_socket_address_get_address(void* value) {
            return javan_inet_socket_address_checked(value)->address;
        }

        void* javan_inet_socket_address_to_string(void* value) {
            javan_inet_socket_address* socket_address = javan_inet_socket_address_checked(value);
            void* socket_address_root = value;
            void** javan_inet_socket_to_string_roots[] = {
                (void**) &socket_address_root
            };
            javan_root_frame_push(javan_inet_socket_to_string_roots, 1);
            socket_address = javan_inet_socket_address_checked(socket_address_root);
            const char* host = socket_address->address->host_name;
            const char* address = socket_address->address->host_address;
            char port_buffer[32];
            snprintf(port_buffer, sizeof(port_buffer), "%d", socket_address->port);
            unsigned long host_length = strlen(host);
            unsigned long address_length = strlen(address);
            unsigned long port_length = strlen(port_buffer);
            int unresolved = socket_address->reserved0 != 0;
            const char* unresolved_suffix = "/<unresolved>:";
            unsigned long unresolved_suffix_length = strlen(unresolved_suffix);
            unsigned long size = 0;
            if (unresolved != 0 && strcmp(host, address) == 0) {
                size = 1UL + address_length + 1UL + port_length + 1UL;
            } else if (unresolved != 0) {
                size = host_length + unresolved_suffix_length + port_length + 1UL;
            } else {
                size = host_length + 1UL + address_length + 1UL + port_length + 1UL;
            }
            char* result = javan_string_alloc(size);
            if (unresolved != 0 && strcmp(host, address) == 0) {
                result[0] = '/';
                memcpy(result + 1, address, address_length);
                result[1 + address_length] = ':';
                memcpy(result + 1 + address_length + 1, port_buffer, port_length + 1UL);
            } else if (unresolved != 0) {
                memcpy(result, host, host_length);
                memcpy(result + host_length, unresolved_suffix, unresolved_suffix_length);
                memcpy(result + host_length + unresolved_suffix_length, port_buffer, port_length + 1UL);
            } else {
                memcpy(result, host, host_length);
                result[host_length] = '/';
                memcpy(result + host_length + 1UL, address, address_length);
                result[host_length + 1UL + address_length] = ':';
                memcpy(result + host_length + 1UL + address_length + 1UL, port_buffer, port_length + 1UL);
            }
            javan_root_frame_pop(javan_inet_socket_to_string_roots);
            return result;
        }

        static int javan_socket_native_close(int fd) {
        #if defined(_WIN32)
            return closesocket((SOCKET) fd);
        #else
            return close(fd);
        #endif
        }

        static void javan_socket_runtime_unsupported(void) {
            javan_panic("tcp sockets are not supported on this host yet");
        }

        static void javan_socket_host_checked(const char* host, struct sockaddr_in* address, int port) {
            if (port < 0 || port > 65535) {
                javan_panic("socket port out of range");
            }
            memset(address, 0, sizeof(*address));
            address->sin_family = AF_INET;
            address->sin_port = htons((unsigned short) port);
            if (host == NULL || strcmp(host, "localhost") == 0) {
                address->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
                return;
            }
            if (inet_pton(AF_INET, host, &address->sin_addr) != 1) {
                javan_panic("unsupported socket host");
            }
        }

        static void* javan_inet_address_from_sockaddr(const struct sockaddr_in* address) {
            char host[INET_ADDRSTRLEN];
            if (inet_ntop(AF_INET, (const void*) &address->sin_addr, host, sizeof(host)) == NULL) {
                javan_panic("socket address conversion failed");
            }
            const char* name = strcmp(host, "127.0.0.1") == 0 ? "localhost" : host;
            return javan_inet_address_new(host, name);
        }

        static void javan_socket_populate_names(int fd, void** local_address_out, int* local_port_out, void** remote_address_out, int* remote_port_out) {
            struct sockaddr_in local_address;
            socklen_t local_length = sizeof(local_address);
            if (getsockname(fd, (struct sockaddr*) &local_address, &local_length) != 0) {
                javan_panic("socket local address lookup failed");
            }
            struct sockaddr_in remote_address;
            socklen_t remote_length = sizeof(remote_address);
            if (getpeername(fd, (struct sockaddr*) &remote_address, &remote_length) != 0) {
                javan_panic("socket remote address lookup failed");
            }
            *local_address_out = javan_inet_address_from_sockaddr(&local_address);
            *remote_address_out = javan_inet_address_from_sockaddr(&remote_address);
            *local_port_out = (int) ntohs(local_address.sin_port);
            *remote_port_out = (int) ntohs(remote_address.sin_port);
        }

        static void* javan_socket_wrap_connected_fd(int fd) {
        #if defined(_WIN32)
            (void) fd;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            void* local_address = NULL;
            void* remote_address = NULL;
            int local_port = 0;
            int remote_port = 0;
            void** javan_socket_wrap_roots[] = {
                (void**) &local_address,
                (void**) &remote_address
            };
            javan_root_frame_push(javan_socket_wrap_roots, 2);
            javan_socket_populate_names(fd, &local_address, &local_port, &remote_address, &remote_port);
            javan_socket* socket = (javan_socket*) javan_alloc(sizeof(javan_socket));
            void* socket_root = (void*) socket;
            void** javan_socket_owner_roots[] = {
                (void**) &local_address,
                (void**) &remote_address,
                (void**) &socket_root
            };
            javan_root_frame_push(javan_socket_owner_roots, 3);
            socket->magic = JAVAN_SOCKET_MAGIC;
            socket->fd = fd;
            socket->connected = 1;
            socket->closed = 0;
            socket->local_port = local_port;
            socket->remote_port = remote_port;
            socket->local_address = (javan_inet_address*) local_address;
            socket->remote_address = (javan_inet_address*) remote_address;
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SOCKET);
            javan_root_frame_pop(javan_socket_owner_roots);
            javan_root_frame_pop(javan_socket_wrap_roots);
            return socket;
        #endif
        }

        static javan_socket* javan_socket_checked(void* value) {
            if (value == NULL) {
                javan_panic("null socket");
            }
            javan_socket* socket = (javan_socket*) value;
            if (socket->magic != JAVAN_SOCKET_MAGIC) {
                javan_panic("unsupported socket object");
            }
            return socket;
        }

        static javan_server_socket* javan_server_socket_checked(void* value) {
            if (value == NULL) {
                javan_panic("null server socket");
            }
            javan_server_socket* socket = (javan_server_socket*) value;
            if (socket->magic != JAVAN_SERVER_SOCKET_MAGIC) {
                javan_panic("unsupported server socket object");
            }
            return socket;
        }

        static javan_socket_input_stream_value* javan_socket_input_stream_checked(void* value) {
            if (value == NULL) {
                javan_panic("null socket input stream");
            }
            javan_socket_input_stream_value* stream = (javan_socket_input_stream_value*) value;
            if (stream->magic != JAVAN_SOCKET_INPUT_STREAM_MAGIC || stream->socket == NULL) {
                javan_panic("unsupported socket input stream object");
            }
            return stream;
        }

        static javan_socket_output_stream_value* javan_socket_output_stream_checked(void* value) {
            if (value == NULL) {
                javan_panic("null socket output stream");
            }
            javan_socket_output_stream_value* stream = (javan_socket_output_stream_value*) value;
            if (stream->magic != JAVAN_SOCKET_OUTPUT_STREAM_MAGIC || stream->socket == NULL) {
                javan_panic("unsupported socket output stream object");
            }
            return stream;
        }

        static javan_socket* javan_socket_open_checked(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0 || socket->fd < 0) {
                javan_panic("socket is closed");
            }
            return socket;
        }

        static void javan_socket_stream_range_checked(javan_byte_array* bytes, int offset, int length) {
            if (offset < 0 || length < 0 || offset > bytes->length || length > bytes->length - offset) {
                javan_panic("socket stream range out of bounds");
            }
        }

        static void* javan_socket_stream_new(void* socket_value, int output_stream) {
            javan_socket* socket = javan_socket_checked(socket_value);
            if (output_stream != 0) {
                javan_socket_output_stream_value* stream = (javan_socket_output_stream_value*) javan_alloc(sizeof(javan_socket_output_stream_value));
                void* stream_root = (void*) stream;
                void* socket_root = socket_value;
                void** javan_socket_output_stream_roots[] = {
                    (void**) &socket_root,
                    (void**) &stream_root
                };
                javan_root_frame_push(javan_socket_output_stream_roots, 2);
                stream = (javan_socket_output_stream_value*) stream_root;
                stream->magic = JAVAN_SOCKET_OUTPUT_STREAM_MAGIC;
                stream->reserved0 = 0;
                stream->reserved1 = 0;
                stream->reserved2 = 0;
                stream->socket = (javan_socket*) socket_root;
                javan_update_runtime_allocation_kind(stream_root, JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM);
                javan_root_frame_pop(javan_socket_output_stream_roots);
                return stream;
            }
            javan_socket_input_stream_value* stream = (javan_socket_input_stream_value*) javan_alloc(sizeof(javan_socket_input_stream_value));
            void* stream_root = (void*) stream;
            void* socket_root = socket_value;
            void** javan_socket_input_stream_roots[] = {
                (void**) &socket_root,
                (void**) &stream_root
            };
            javan_root_frame_push(javan_socket_input_stream_roots, 2);
            stream = (javan_socket_input_stream_value*) stream_root;
            stream->magic = JAVAN_SOCKET_INPUT_STREAM_MAGIC;
            stream->reserved0 = 0;
            stream->reserved1 = 0;
            stream->reserved2 = 0;
            stream->socket = (javan_socket*) socket_root;
            javan_update_runtime_allocation_kind(stream_root, JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM);
            javan_root_frame_pop(javan_socket_input_stream_roots);
            return stream;
        }

        void* javan_socket_connect_host(void* host_value, int port) {
        #if defined(_WIN32)
            (void) host_value;
            (void) port;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_in address;
            javan_socket_host_checked(host, &address, port);
            int fd = socket(AF_INET, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("socket open failed");
            }
            if (connect(fd, (struct sockaddr*) &address, sizeof(address)) != 0) {
                javan_socket_native_close(fd);
                javan_panic("socket connect failed");
            }
            return javan_socket_wrap_connected_fd(fd);
        #endif
        }

        int javan_socket_is_connected(void* value) {
            return javan_socket_checked(value)->connected != 0;
        }

        int javan_socket_is_closed(void* value) {
            return javan_socket_checked(value)->closed != 0;
        }

        int javan_socket_get_port(void* value) {
            return javan_socket_checked(value)->remote_port;
        }

        int javan_socket_get_local_port(void* value) {
            return javan_socket_checked(value)->local_port;
        }

        void* javan_socket_get_inet_address(void* value) {
            return javan_socket_checked(value)->remote_address;
        }

        void* javan_socket_input_stream(void* value) {
            return javan_socket_stream_new(value, 0);
        }

        void* javan_socket_output_stream(void* value) {
            return javan_socket_stream_new(value, 1);
        }

        int javan_socket_input_stream_read(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return 0;
        #else
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            unsigned char byte = 0;
            ssize_t result = recv(socket->fd, &byte, 1, 0);
            if (result < 0) {
                javan_panic("socket read failed");
            }
            if (result == 0) {
                return -1;
            }
            return byte;
        #endif
        }

        int javan_socket_input_stream_read_bytes(void* value, void* bytes_value) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            return javan_socket_input_stream_read_bytes_range(value, bytes_value, 0, bytes->length);
        }

        int javan_socket_input_stream_read_bytes_range(void* value, void* bytes_value, int offset, int length) {
        #if defined(_WIN32)
            (void) value;
            (void) bytes_value;
            (void) offset;
            (void) length;
            javan_socket_runtime_unsupported();
            return 0;
        #else
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_stream_range_checked(bytes, offset, length);
            if (length == 0) {
                return 0;
            }
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            ssize_t result = recv(socket->fd, bytes->values + offset, (size_t) length, 0);
            if (result < 0) {
                javan_panic("socket read failed");
            }
            if (result == 0) {
                return -1;
            }
            return (int) result;
        #endif
        }

        void javan_socket_input_stream_close(void* value) {
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket_close((void*) stream->socket);
        }

        void javan_socket_output_stream_write(void* value, int byte_value) {
        #if defined(_WIN32)
            (void) value;
            (void) byte_value;
            javan_socket_runtime_unsupported();
        #else
            javan_socket_output_stream_value* stream = javan_socket_output_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            unsigned char byte = (unsigned char) (byte_value & 0xff);
            ssize_t written = send(socket->fd, &byte, 1, 0);
            if (written != 1) {
                javan_panic("socket write failed");
            }
        #endif
        }

        void javan_socket_output_stream_write_bytes(void* value, void* bytes_value) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_output_stream_write_bytes_range(value, bytes_value, 0, bytes->length);
        }

        void javan_socket_output_stream_write_bytes_range(void* value, void* bytes_value, int offset, int length) {
        #if defined(_WIN32)
            (void) value;
            (void) bytes_value;
            (void) offset;
            (void) length;
            javan_socket_runtime_unsupported();
        #else
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_stream_range_checked(bytes, offset, length);
            if (length == 0) {
                return;
            }
            javan_socket_output_stream_value* stream = javan_socket_output_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            int written = 0;
            while (written < length) {
                ssize_t chunk = send(socket->fd, bytes->values + offset + written, (size_t) (length - written), 0);
                if (chunk <= 0) {
                    javan_panic("socket write failed");
                }
                written += (int) chunk;
            }
        #endif
        }

        void javan_socket_output_stream_flush(void* value) {
            (void) javan_socket_output_stream_checked(value);
        }

        void javan_socket_output_stream_close(void* value) {
            javan_socket_output_stream_value* stream = javan_socket_output_stream_checked(value);
            javan_socket_close((void*) stream->socket);
        }

        void javan_socket_close(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->fd >= 0) {
                javan_socket_native_close(socket->fd);
                socket->fd = -1;
            }
            socket->closed = 1;
        }

        void* javan_server_socket_bind(int port) {
        #if defined(_WIN32)
            (void) port;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            if (port < 0 || port > 65535) {
                javan_panic("socket port out of range");
            }
            int fd = socket(AF_INET, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("server socket open failed");
            }
            int reuse = 1;
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
            struct sockaddr_in address;
            memset(&address, 0, sizeof(address));
            address.sin_family = AF_INET;
            address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
            address.sin_port = htons((unsigned short) port);
            if (bind(fd, (struct sockaddr*) &address, sizeof(address)) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket bind failed");
            }
            if (listen(fd, 16) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket listen failed");
            }
            struct sockaddr_in bound;
            socklen_t bound_length = sizeof(bound);
            if (getsockname(fd, (struct sockaddr*) &bound, &bound_length) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket local address lookup failed");
            }
            void* local_address = NULL;
            void** javan_server_socket_roots[] = {
                (void**) &local_address
            };
            javan_root_frame_push(javan_server_socket_roots, 1);
            local_address = javan_inet_address_from_sockaddr(&bound);
            javan_server_socket* socket = (javan_server_socket*) javan_alloc(sizeof(javan_server_socket));
            void* socket_root = (void*) socket;
            void** javan_server_socket_owner_roots[] = {
                (void**) &local_address,
                (void**) &socket_root
            };
            javan_root_frame_push(javan_server_socket_owner_roots, 2);
            socket->magic = JAVAN_SERVER_SOCKET_MAGIC;
            socket->fd = fd;
            socket->closed = 0;
            socket->local_port = (int) ntohs(bound.sin_port);
            socket->reserved0 = 0;
            socket->reserved1 = 0;
            socket->local_address = (javan_inet_address*) local_address;
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SERVER_SOCKET);
            javan_root_frame_pop(javan_server_socket_owner_roots);
            javan_root_frame_pop(javan_server_socket_roots);
            return socket;
        #endif
        }

        int javan_server_socket_get_local_port(void* value) {
            return javan_server_socket_checked(value)->local_port;
        }

        void* javan_server_socket_accept(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            javan_server_socket* server = javan_server_socket_checked(value);
            if (server->closed != 0 || server->fd < 0) {
                javan_panic("server socket is closed");
            }
            int accepted = accept(server->fd, NULL, NULL);
            if (accepted < 0) {
                javan_panic("server socket accept failed");
            }
            return javan_socket_wrap_connected_fd(accepted);
        #endif
        }

        void javan_server_socket_close(void* value) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->fd >= 0) {
                javan_socket_native_close(socket->fd);
                socket->fd = -1;
            }
            socket->closed = 1;
        }
        """;

    private static final String SOURCE_HTTP = """
        static char* javan_http_copy_range(const char* value, unsigned long length) {
            char* result = javan_string_alloc(length + 1UL);
            if (length > 0) {
                memcpy(result, value, length);
            }
            result[length] = '\\0';
            return result;
        }

        static javan_uri_value* javan_uri_checked(void* value) {
            if (value == NULL) {
                javan_panic("null uri");
            }
            javan_uri_value* uri = (javan_uri_value*) value;
            if (uri->magic != JAVAN_URI_MAGIC) {
                javan_panic("unsupported uri object");
            }
            return uri;
        }

        static javan_http_client_value* javan_http_client_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http client");
            }
            javan_http_client_value* client = (javan_http_client_value*) value;
            if (client->magic != JAVAN_HTTP_CLIENT_MAGIC) {
                javan_panic("unsupported http client object");
            }
            return client;
        }

        static javan_http_request_builder_value* javan_http_request_builder_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http request builder");
            }
            javan_http_request_builder_value* builder = (javan_http_request_builder_value*) value;
            if (builder->magic != JAVAN_HTTP_REQUEST_BUILDER_MAGIC || builder->uri == NULL || builder->headers == NULL) {
                javan_panic("unsupported http request builder object");
            }
            return builder;
        }

        static javan_http_request_value* javan_http_request_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http request");
            }
            javan_http_request_value* request = (javan_http_request_value*) value;
            if (request->magic != JAVAN_HTTP_REQUEST_MAGIC || request->uri == NULL || request->headers == NULL) {
                javan_panic("unsupported http request object");
            }
            return request;
        }

        static javan_http_body_publisher_value* javan_http_body_publisher_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http body publisher");
            }
            javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) value;
            if (publisher->magic != JAVAN_HTTP_BODY_PUBLISHER_MAGIC
                || (publisher->kind != JAVAN_HTTP_BODY_KIND_STRING && publisher->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)
                || publisher->value == NULL) {
                javan_panic("unsupported http body publisher object");
            }
            return publisher;
        }

        static javan_http_body_handler_value* javan_http_body_handler_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http body handler");
            }
            javan_http_body_handler_value* handler = (javan_http_body_handler_value*) value;
            if (handler->magic != JAVAN_HTTP_BODY_HANDLER_MAGIC
                || (handler->kind != JAVAN_HTTP_BODY_KIND_STRING && handler->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)) {
                javan_panic("unsupported http body handler object");
            }
            return handler;
        }

        static javan_http_response_value* javan_http_response_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http response");
            }
            javan_http_response_value* response = (javan_http_response_value*) value;
            if (response->magic != JAVAN_HTTP_RESPONSE_MAGIC || response->body == NULL) {
                javan_panic("unsupported http response object");
            }
            return response;
        }

        static void javan_http_header_text_checked(const char* value, const char* kind) {
            if (value == NULL) {
                javan_panic(kind);
            }
            for (const char* cursor = value; cursor[0] != '\\0'; cursor++) {
                if (cursor[0] == '\\r' || cursor[0] == '\\n') {
                    javan_panic("http header contains a newline");
                }
            }
        }

        static void javan_http_parse_uri_text(const char* text, char** scheme_out, char** host_out, int* port_out, char** target_out) {
            if (text == NULL) {
                javan_panic("null uri text");
            }
            const char* prefix = "http://";
            unsigned long prefix_length = strlen(prefix);
            if (strncmp(text, prefix, prefix_length) != 0) {
                javan_panic("unsupported uri scheme");
            }
            const char* authority = text + prefix_length;
            if (authority[0] == '\\0') {
                javan_panic("uri host is missing");
            }
            const char* cursor = authority;
            while (cursor[0] != '\\0' && cursor[0] != '/' && cursor[0] != '?') {
                cursor++;
            }
            const char* host_end = cursor;
            int port = 80;
            const char* colon = NULL;
            for (const char* scan = authority; scan < host_end; scan++) {
                if (scan[0] == ':') {
                    colon = scan;
                }
            }
            if (colon != NULL) {
                host_end = colon;
                const char* port_text = colon + 1;
                if (port_text >= cursor) {
                    javan_panic("uri port is missing");
                }
                port = 0;
                while (port_text < cursor) {
                    if (port_text[0] < '0' || port_text[0] > '9') {
                        javan_panic("uri port is invalid");
                    }
                    port = port * 10 + (port_text[0] - '0');
                    port_text++;
                }
                if (port < 0 || port > 65535) {
                    javan_panic("uri port is out of range");
                }
            }
            if (host_end <= authority) {
                javan_panic("uri host is missing");
            }
            const char* target = cursor;
            if (target[0] == '\\0') {
                target = "/";
            } else if (target[0] == '?') {
                unsigned long query_length = strlen(target);
                char* with_slash = javan_string_alloc(query_length + 2UL);
                with_slash[0] = '/';
                memcpy(with_slash + 1UL, target, query_length + 1UL);
                *scheme_out = javan_http_copy_range(prefix, prefix_length - 3UL);
                *host_out = javan_http_copy_range(authority, (unsigned long) (host_end - authority));
                *target_out = with_slash;
                *port_out = port;
                return;
            }
            *scheme_out = javan_http_copy_range(prefix, prefix_length - 3UL);
            *host_out = javan_http_copy_range(authority, (unsigned long) (host_end - authority));
            *target_out = javan_http_copy_range(target, strlen(target));
            *port_out = port;
        }

        void* javan_uri_create(void* value) {
            char* scheme = NULL;
            char* host = NULL;
            char* target = NULL;
            int port = 80;
            void** javan_uri_parse_roots[] = {
                (void**) &scheme,
                (void**) &host,
                (void**) &target
            };
            javan_root_frame_push(javan_uri_parse_roots, 3);
            javan_http_parse_uri_text((const char*) value, &scheme, &host, &port, &target);
            javan_uri_value* uri = (javan_uri_value*) javan_alloc(sizeof(javan_uri_value));
            void* uri_root = (void*) uri;
            void** javan_uri_owner_roots[] = {
                (void**) &scheme,
                (void**) &host,
                (void**) &target,
                (void**) &uri_root
            };
            javan_root_frame_push(javan_uri_owner_roots, 4);
            uri = (javan_uri_value*) uri_root;
            uri->magic = JAVAN_URI_MAGIC;
            uri->port = port;
            uri->reserved0 = 0;
            uri->reserved1 = 0;
            uri->scheme = scheme;
            uri->host = host;
            uri->target = target;
            javan_update_runtime_allocation_kind(uri_root, JAVAN_RUNTIME_KIND_URI);
            javan_root_frame_pop(javan_uri_owner_roots);
            javan_root_frame_pop(javan_uri_parse_roots);
            return uri;
        }

        void* javan_http_client_new(void) {
            javan_http_client_value* client = (javan_http_client_value*) javan_alloc(sizeof(javan_http_client_value));
            client->magic = JAVAN_HTTP_CLIENT_MAGIC;
            client->reserved0 = 0;
            client->reserved1 = 0;
            client->reserved2 = 0;
            javan_update_runtime_allocation_kind((void*) client, JAVAN_RUNTIME_KIND_HTTP_CLIENT);
            return client;
        }

        void* javan_http_request_builder_new(void* uri_value) {
            javan_uri_value* uri = javan_uri_checked(uri_value);
            void* uri_root = (void*) uri;
            javan_http_request_builder_value* builder = (javan_http_request_builder_value*) javan_alloc(sizeof(javan_http_request_builder_value));
            void* builder_root = (void*) builder;
            void** javan_http_builder_setup_roots[] = {
                (void**) &uri_root,
                (void**) &builder_root
            };
            javan_root_frame_push(javan_http_builder_setup_roots, 2);
            void* headers_root = javan_list_new_with_capacity(0, 0);
            void** javan_http_builder_roots[] = {
                (void**) &uri_root,
                (void**) &builder_root,
                (void**) &headers_root
            };
            javan_root_frame_push(javan_http_builder_roots, 3);
            builder = (javan_http_request_builder_value*) builder_root;
            builder->magic = JAVAN_HTTP_REQUEST_BUILDER_MAGIC;
            builder->method = JAVAN_HTTP_METHOD_GET;
            builder->reserved0 = 0;
            builder->reserved1 = 0;
            builder->uri = (javan_uri_value*) uri_root;
            builder->headers = (javan_object_list*) headers_root;
            builder->body = NULL;
            javan_update_runtime_allocation_kind(builder_root, JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER);
            javan_root_frame_pop(javan_http_builder_roots);
            javan_root_frame_pop(javan_http_builder_setup_roots);
            return builder_root;
        }

        void* javan_http_request_builder_get(void* value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            builder->method = JAVAN_HTTP_METHOD_GET;
            builder->body = NULL;
            return builder;
        }

        void* javan_http_request_builder_header(void* value, void* name_value, void* header_value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            javan_http_header_text_checked((const char*) name_value, "null http header name");
            javan_http_header_text_checked((const char*) header_value, "null http header value");
            void* builder_root = value;
            void* name_root = name_value;
            void* header_root = header_value;
            void** javan_http_header_roots[] = {
                (void**) &builder_root,
                (void**) &name_root,
                (void**) &header_root
            };
            javan_root_frame_push(javan_http_header_roots, 3);
            builder = (javan_http_request_builder_value*) builder_root;
            javan_list_append_raw(builder->headers, name_root);
            javan_list_append_raw(builder->headers, header_root);
            javan_root_frame_pop(javan_http_header_roots);
            return builder_root;
        }

        void* javan_http_body_publisher_string(void* value) {
            if (value == NULL) {
                javan_panic("null http request body");
            }
            javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) javan_alloc(sizeof(javan_http_body_publisher_value));
            void* publisher_root = (void*) publisher;
            void* value_root = value;
            void** javan_http_body_publisher_roots[] = {
                (void**) &publisher_root,
                (void**) &value_root
            };
            javan_root_frame_push(javan_http_body_publisher_roots, 2);
            publisher = (javan_http_body_publisher_value*) publisher_root;
            publisher->magic = JAVAN_HTTP_BODY_PUBLISHER_MAGIC;
            publisher->kind = JAVAN_HTTP_BODY_KIND_STRING;
            publisher->reserved0 = 0;
            publisher->reserved1 = 0;
            publisher->value = value_root;
            javan_update_runtime_allocation_kind(publisher_root, JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER);
            javan_root_frame_pop(javan_http_body_publisher_roots);
            return publisher_root;
        }

        void* javan_http_body_publisher_byte_array(void* value) {
            if (value == NULL) {
                javan_panic("null http request body");
            }
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) javan_alloc(sizeof(javan_http_body_publisher_value));
            void* publisher_root = (void*) publisher;
            void* value_root = value;
            void** javan_http_body_publisher_roots[] = {
                (void**) &publisher_root,
                (void**) &value_root
            };
            javan_root_frame_push(javan_http_body_publisher_roots, 2);
            publisher = (javan_http_body_publisher_value*) publisher_root;
            publisher->magic = JAVAN_HTTP_BODY_PUBLISHER_MAGIC;
            publisher->kind = JAVAN_HTTP_BODY_KIND_BYTE_ARRAY;
            publisher->reserved0 = 0;
            publisher->reserved1 = 0;
            publisher->value = value_root;
            javan_update_runtime_allocation_kind(publisher_root, JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER);
            javan_root_frame_pop(javan_http_body_publisher_roots);
            return publisher_root;
        }

        void* javan_http_request_builder_post(void* value, void* body_publisher_value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            builder->method = JAVAN_HTTP_METHOD_POST;
            builder->body = (void*) javan_http_body_publisher_checked(body_publisher_value);
            return builder;
        }

        void* javan_http_request_builder_put(void* value, void* body_publisher_value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            builder->method = JAVAN_HTTP_METHOD_PUT;
            builder->body = (void*) javan_http_body_publisher_checked(body_publisher_value);
            return builder;
        }

        void* javan_http_request_builder_build(void* value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            void* uri_root = (void*) builder->uri;
            void* headers_root = (void*) builder->headers;
            void* body_root = builder->body;
            void** javan_http_request_setup_roots[] = {
                (void**) &uri_root,
                (void**) &headers_root,
                (void**) &body_root
            };
            javan_root_frame_push(javan_http_request_setup_roots, 3);
            headers_root = javan_list_copy_of(headers_root);
            javan_http_request_value* request = (javan_http_request_value*) javan_alloc(sizeof(javan_http_request_value));
            void* request_root = (void*) request;
            void** javan_http_request_roots[] = {
                (void**) &uri_root,
                (void**) &headers_root,
                (void**) &body_root,
                (void**) &request_root
            };
            javan_root_frame_push(javan_http_request_roots, 4);
            request = (javan_http_request_value*) request_root;
            request->magic = JAVAN_HTTP_REQUEST_MAGIC;
            request->method = builder->method;
            request->reserved0 = 0;
            request->reserved1 = 0;
            request->uri = (javan_uri_value*) uri_root;
            request->headers = (javan_object_list*) headers_root;
            request->body = body_root;
            javan_update_runtime_allocation_kind(request_root, JAVAN_RUNTIME_KIND_HTTP_REQUEST);
            javan_root_frame_pop(javan_http_request_roots);
            javan_root_frame_pop(javan_http_request_setup_roots);
            return request_root;
        }

        void* javan_http_body_handler_string(void) {
            javan_http_body_handler_value* handler = (javan_http_body_handler_value*) javan_alloc(sizeof(javan_http_body_handler_value));
            handler->magic = JAVAN_HTTP_BODY_HANDLER_MAGIC;
            handler->kind = JAVAN_HTTP_BODY_KIND_STRING;
            handler->reserved0 = 0;
            handler->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) handler, JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER);
            return handler;
        }

        void* javan_http_body_handler_byte_array(void) {
            javan_http_body_handler_value* handler = (javan_http_body_handler_value*) javan_alloc(sizeof(javan_http_body_handler_value));
            handler->magic = JAVAN_HTTP_BODY_HANDLER_MAGIC;
            handler->kind = JAVAN_HTTP_BODY_KIND_BYTE_ARRAY;
            handler->reserved0 = 0;
            handler->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) handler, JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER);
            return handler;
        }

        static void javan_http_send_all(int fd, const char* bytes, unsigned long length) {
            unsigned long offset = 0;
            while (offset < length) {
                ssize_t sent = send(fd, bytes + offset, (size_t) (length - offset), 0);
                if (sent <= 0) {
                    javan_panic("http request write failed");
                }
                offset += (unsigned long) sent;
            }
        }

        static char* javan_http_read_all(int fd, unsigned long* length_out) {
            unsigned long capacity = 1024;
            unsigned long length = 0;
            char* buffer = (char*) malloc(capacity + 1UL);
            if (buffer == NULL) {
                javan_panic("out of memory");
            }
            while (1) {
                if (length == capacity) {
                    unsigned long next_capacity = capacity * 2UL;
                    char* next = (char*) realloc(buffer, next_capacity + 1UL);
                    if (next == NULL) {
                        free(buffer);
                        javan_panic("out of memory");
                    }
                    buffer = next;
                    capacity = next_capacity;
                }
                ssize_t received = recv(fd, buffer + length, (size_t) (capacity - length), 0);
                if (received < 0) {
                    free(buffer);
                    javan_panic("http response read failed");
                }
                if (received == 0) {
                    break;
                }
                length += (unsigned long) received;
            }
            buffer[length] = '\\0';
            *length_out = length;
            return buffer;
        }

        static int javan_http_parse_status_code(const char* response, unsigned long length, const char** body_start_out) {
            if (length < 12UL || strncmp(response, "HTTP/1.", 7) != 0) {
                javan_panic("invalid http response");
            }
            const char* status = strchr(response, ' ');
            if (status == NULL || status[1] < '0' || status[1] > '9') {
                javan_panic("invalid http status");
            }
            int status_code = 0;
            status++;
            for (int index = 0; index < 3; index++) {
                if (status[index] < '0' || status[index] > '9') {
                    javan_panic("invalid http status");
                }
                status_code = status_code * 10 + (status[index] - '0');
            }
            const char* body = strstr(response, "\\r\\n\\r\\n");
            if (body != NULL) {
                *body_start_out = body + 4;
                return status_code;
            }
            body = strstr(response, "\\n\\n");
            if (body != NULL) {
                *body_start_out = body + 2;
                return status_code;
            }
            javan_panic("invalid http response");
            return 0;
        }

        static unsigned long javan_http_body_publisher_length(javan_http_body_publisher_value* publisher) {
            if (publisher == NULL) {
                return 0;
            }
            if (publisher->kind == JAVAN_HTTP_BODY_KIND_STRING) {
                return strlen((const char*) publisher->value);
            }
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(publisher->value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            return (unsigned long) bytes->length;
        }

        static const signed char* javan_http_body_publisher_bytes(javan_http_body_publisher_value* publisher) {
            if (publisher == NULL) {
                return NULL;
            }
            if (publisher->kind == JAVAN_HTTP_BODY_KIND_STRING) {
                return (const signed char*) publisher->value;
            }
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(publisher->value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            return bytes->values;
        }

        void* javan_http_client_send(void* client_value, void* request_value, void* body_handler_value) {
        #if defined(_WIN32)
            (void) client_value;
            (void) request_value;
            (void) body_handler_value;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            void** javan_http_send_roots[] = {
                (void**) &client_value,
                (void**) &request_value,
                (void**) &body_handler_value
            };
            javan_root_frame_push(javan_http_send_roots, 3);
            (void) javan_http_client_checked(client_value);
            javan_http_request_value* request = javan_http_request_checked(request_value);
            javan_http_body_handler_value* body_handler = javan_http_body_handler_checked(body_handler_value);
            if (request->method != JAVAN_HTTP_METHOD_GET
                && request->method != JAVAN_HTTP_METHOD_POST
                && request->method != JAVAN_HTTP_METHOD_PUT) {
                javan_panic("http method is not supported yet");
            }
            javan_uri_value* uri = javan_uri_checked((void*) request->uri);
            javan_http_body_publisher_value* body_publisher = request->body == NULL ? NULL : javan_http_body_publisher_checked(request->body);
            struct sockaddr_in address;
            javan_socket_host_checked(uri->host, &address, uri->port);
            int fd = socket(AF_INET, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("http socket open failed");
            }
            if (connect(fd, (struct sockaddr*) &address, sizeof(address)) != 0) {
                javan_socket_native_close(fd);
                javan_panic("http connect failed");
            }
            char port_suffix[32];
            port_suffix[0] = '\\0';
            if (uri->port != 80) {
                snprintf(port_suffix, sizeof(port_suffix), ":%d", uri->port);
            }
            const char* method_text = request->method == JAVAN_HTTP_METHOD_POST
                ? "POST"
                : (request->method == JAVAN_HTTP_METHOD_PUT ? "PUT" : "GET");
            unsigned long request_body_length = javan_http_body_publisher_length(body_publisher);
            unsigned long size = strlen(method_text) + 1UL
                + strlen(uri->target)
                + strlen(" HTTP/1.1\\r\\nHost: ")
                + strlen(uri->host)
                + strlen(port_suffix)
                + strlen("\\r\\nConnection: close\\r\\n");
            if (request->method == JAVAN_HTTP_METHOD_POST || request->method == JAVAN_HTTP_METHOD_PUT) {
                char content_length_buffer[32];
                snprintf(content_length_buffer, sizeof(content_length_buffer), "%lu", request_body_length);
                size += strlen("Content-Length: ") + strlen(content_length_buffer) + 2UL;
            }
            javan_object_list* headers = request->headers;
            for (int index = 0; index < headers->length; index += 2) {
                const char* name = (const char*) headers->values[index];
                const char* header_value = (const char*) headers->values[index + 1];
                javan_http_header_text_checked(name, "null http header name");
                javan_http_header_text_checked(header_value, "null http header value");
                size += strlen(name) + 2UL + strlen(header_value) + 2UL;
            }
            size += 2UL;
            char* request_text = (char*) malloc(size + 1UL);
            if (request_text == NULL) {
                javan_socket_native_close(fd);
                javan_root_frame_pop(javan_http_send_roots);
                javan_panic("out of memory");
            }
            int written = snprintf(
                request_text,
                size + 1UL,
                "%s %s HTTP/1.1\\r\\nHost: %s%s\\r\\nConnection: close\\r\\n",
                method_text,
                uri->target,
                uri->host,
                port_suffix
            );
            if (written < 0) {
                free(request_text);
                javan_socket_native_close(fd);
                javan_root_frame_pop(javan_http_send_roots);
                javan_panic("http request format failed");
            }
            unsigned long offset = (unsigned long) written;
            for (int index = 0; index < headers->length; index += 2) {
                written = snprintf(
                    request_text + offset,
                    size + 1UL - offset,
                    "%s: %s\\r\\n",
                    (const char*) headers->values[index],
                    (const char*) headers->values[index + 1]
                );
                if (written < 0) {
                    free(request_text);
                    javan_socket_native_close(fd);
                    javan_root_frame_pop(javan_http_send_roots);
                    javan_panic("http request header format failed");
                }
                offset += (unsigned long) written;
            }
            if (request->method == JAVAN_HTTP_METHOD_POST || request->method == JAVAN_HTTP_METHOD_PUT) {
                written = snprintf(request_text + offset, size + 1UL - offset, "Content-Length: %lu\\r\\n", request_body_length);
                if (written < 0) {
                    free(request_text);
                    javan_socket_native_close(fd);
                    javan_root_frame_pop(javan_http_send_roots);
                    javan_panic("http request header format failed");
                }
                offset += (unsigned long) written;
            }
            memcpy(request_text + offset, "\\r\\n", 3);
            offset += 2UL;
            javan_http_send_all(fd, request_text, offset);
            if (request_body_length > 0) {
                javan_http_send_all(fd, (const char*) javan_http_body_publisher_bytes(body_publisher), request_body_length);
            }
            free(request_text);
            unsigned long response_length = 0;
            char* response = javan_http_read_all(fd, &response_length);
            javan_socket_native_close(fd);
            const char* body_start = NULL;
            int status_code = javan_http_parse_status_code(response, response_length, &body_start);
            unsigned long response_body_length = response_length - (unsigned long) (body_start - response);
            void* body = NULL;
            if (body_handler->kind == JAVAN_HTTP_BODY_KIND_STRING) {
                body = (void*) javan_http_copy_range(body_start, response_body_length);
            } else {
                body = javan_byte_array_from((const signed char*) body_start, (int) response_body_length);
            }
            free(response);
            javan_http_response_value* http_response = (javan_http_response_value*) javan_alloc(sizeof(javan_http_response_value));
            void* response_root = (void*) http_response;
            void* body_root = (void*) body;
            void** javan_http_response_roots[] = {
                (void**) &body_root,
                (void**) &response_root
            };
            javan_root_frame_push(javan_http_response_roots, 2);
            http_response = (javan_http_response_value*) response_root;
            http_response->magic = JAVAN_HTTP_RESPONSE_MAGIC;
            http_response->status_code = status_code;
            http_response->reserved0 = 0;
            http_response->reserved1 = 0;
            http_response->body = body_root;
            javan_update_runtime_allocation_kind(response_root, JAVAN_RUNTIME_KIND_HTTP_RESPONSE);
            javan_root_frame_pop(javan_http_response_roots);
            javan_root_frame_pop(javan_http_send_roots);
            return response_root;
        #endif
        }

        int javan_http_response_status_code(void* response) {
            return javan_http_response_checked(response)->status_code;
        }

        void* javan_http_response_body(void* response) {
            return javan_http_response_checked(response)->body;
        }
        """;

    private static final String SOURCE_FILES = """
        int javan_files_exists(void* path, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            return javan_stat_path(javan_path_checked(path), no_follow, &info) == 0;
        }

        int javan_files_is_directory(void* path, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            return javan_stat_path(javan_path_checked(path), no_follow, &info) == 0 && S_ISDIR(info.st_mode);
        }

        int javan_files_is_regular_file(void* path, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            return javan_stat_path(javan_path_checked(path), no_follow, &info) == 0 && S_ISREG(info.st_mode);
        }

        int javan_files_is_executable(void* path) {
            return access(javan_path_checked(path), X_OK) == 0;
        }

        void* javan_files_copy(void* source, void* target, void* options) {
            javan_copy_options_checked(options);
            const char* source_path = javan_path_checked(source);
            const char* target_path = javan_path_checked(target);
            FILE* input = fopen(source_path, "rb");
            if (input == NULL) {
                javan_panic("file copy source open failed");
            }
            javan_native_resource_frame input_resource;
            javan_native_resource_push(&input_resource, input, javan_native_file_cleanup);
            FILE* output = fopen(target_path, "wb");
            if (output == NULL) {
                javan_native_resource_pop(&input_resource);
                fclose(input);
                javan_panic("file copy target open failed");
            }
            javan_native_resource_frame output_resource;
            javan_native_resource_push(&output_resource, output, javan_native_file_cleanup);
            char buffer[8192];
            size_t read = 0;
            while ((read = fread(buffer, 1, sizeof(buffer), input)) > 0) {
                if (fwrite(buffer, 1, read, output) != read) {
                    javan_native_resource_pop(&output_resource);
                    fclose(output);
                    javan_native_resource_pop(&input_resource);
                    fclose(input);
                    javan_panic("file copy write failed");
                }
            }
            if (ferror(input)) {
                javan_native_resource_pop(&output_resource);
                fclose(output);
                javan_native_resource_pop(&input_resource);
                fclose(input);
                javan_panic("file copy read failed");
            }
            javan_native_resource_pop(&output_resource);
            fclose(output);
            javan_native_resource_pop(&input_resource);
            fclose(input);
            return (void*) target_path;
        }

        static void javan_mkdir_if_needed(const char* path) {
            if (path[0] == '\\0') {
                return;
            }
            if (mkdir(path, 0777) == 0) {
                return;
            }
            if (errno == EEXIST) {
                struct stat info;
                if (stat(path, &info) == 0 && S_ISDIR(info.st_mode)) {
                    return;
                }
            }
            javan_panic("createDirectories failed");
        }

        void* javan_files_create_directories(void* path_value, void* attributes) {
            javan_empty_options_checked(attributes);
            const char* path = javan_path_checked(path_value);
            char* copy = (char*) javan_string_copy(path);
            for (char* cursor = copy + 1; *cursor != '\\0'; cursor++) {
                if (*cursor == '/') {
                    *cursor = '\\0';
                    javan_mkdir_if_needed(copy);
                    *cursor = '/';
                }
            }
            javan_mkdir_if_needed(copy);
            javan_free(copy);
            return path_value;
        }

        void* javan_files_read_string(void* path_value) {
            const char* path = javan_path_checked(path_value);
            FILE* file = fopen(path, "rb");
            if (file == NULL) {
                javan_panic("readString failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            if (fseek(file, 0, SEEK_END) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            long length = ftell(file);
            if (length < 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            if (fseek(file, 0, SEEK_SET) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            char* result = javan_string_alloc((unsigned long) length + 1);
            if (length > 0 && fread(result, 1, (unsigned long) length, file) != (unsigned long) length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            result[length] = '\\0';
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return result;
        }

        void* javan_files_write_string(void* path_value, void* value, void* options) {
            javan_empty_options_checked(options);
            const char* path = javan_path_checked(path_value);
            const char* text = value == NULL ? "" : (const char*) value;
            FILE* file = fopen(path, "wb");
            if (file == NULL) {
                javan_panic("writeString failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            unsigned long length = strlen(text);
            if (length > 0 && fwrite(text, 1, length, file) != length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("writeString failed");
            }
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return path_value;
        }

        void* javan_files_write_bytes(void* path_value, void* bytes_value, void* options) {
            javan_empty_options_checked(options);
            const char* path = javan_path_checked(path_value);
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            FILE* file = fopen(path, "wb");
            if (file == NULL) {
                javan_panic("write bytes failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            if (bytes->length > 0
                && fwrite(bytes->values, 1, (unsigned long) bytes->length, file) != (unsigned long) bytes->length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("write bytes failed");
            }
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return path_value;
        }

        void* javan_files_read_all_bytes(void* path_value) {
            const char* path = javan_path_checked(path_value);
            FILE* file = fopen(path, "rb");
            if (file == NULL) {
                javan_panic("readAllBytes failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            if (fseek(file, 0, SEEK_END) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            long length = ftell(file);
            if (length < 0 || length > INT_MAX) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            if (fseek(file, 0, SEEK_SET) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            void* result = javan_byte_array_new((int) length);
            javan_byte_array* array = (javan_byte_array*) result;
            if (length > 0 && fread(array->values, 1, (unsigned long) length, file) != (unsigned long) length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return result;
        }

        int javan_files_delete_if_exists(void* path_value) {
            const char* path = javan_path_checked(path_value);
            if (remove(path) == 0) {
                return 1;
            }
            if (errno == ENOENT) {
                return 0;
            }
            javan_panic("deleteIfExists failed");
            return 0;
        }

        long long javan_files_size(void* path_value) {
            struct stat info;
            if (stat(javan_path_checked(path_value), &info) != 0) {
                javan_panic("Files.size failed");
            }
            return (long long) info.st_size;
        }

        static long long javan_file_time_modified_millis(const struct stat* info) {
        #if defined(__APPLE__) || defined(__MACH__)
            return ((long long) info->st_mtimespec.tv_sec * 1000LL) + ((long long) info->st_mtimespec.tv_nsec / 1000000LL);
        #elif defined(_WIN32)
            return ((long long) info->st_mtime * 1000LL);
        #else
            return ((long long) info->st_mtim.tv_sec * 1000LL) + ((long long) info->st_mtim.tv_nsec / 1000000LL);
        #endif
        }

        void* javan_files_get_last_modified_time(void* path_value, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            if (javan_stat_path(javan_path_checked(path_value), no_follow, &info) != 0) {
                javan_panic("getLastModifiedTime failed");
            }
            return javan_file_time_from_millis(javan_file_time_modified_millis(&info));
        }

        static void javan_directory_stream_insert_sorted(javan_object_list* list, void* value) {
            const char* path = javan_path_checked(value);
            int index = 0;
            while (index < list->length && strcmp(javan_path_checked(list->values[index]), path) <= 0) {
                index++;
            }
            javan_list_ensure_capacity(list, list->length + 1);
            if (index < list->length) {
                memmove(list->values + index + 1, list->values + index, (unsigned long) (list->length - index) * sizeof(void*));
            }
            list->values[index] = value;
            list->length++;
        }

        void* javan_files_new_directory_stream(void* path_value) {
            void* source_root = path_value;
            void* result_root = NULL;
            void** javan_directory_result_roots[] = {
                (void**) &source_root,
                (void**) &result_root
            };
            javan_root_frame_push(javan_directory_result_roots, 2);
            const char* path = javan_path_checked(source_root);
            result_root = javan_list_new_with_capacity(0, 1);
            javan_object_list* result = (javan_object_list*) result_root;
            DIR* directory = opendir(path);
            if (directory == NULL) {
                javan_panic("newDirectoryStream failed");
            }
            javan_native_resource_frame directory_resource;
            javan_native_resource_push(&directory_resource, directory, javan_native_dir_cleanup);
            struct dirent* entry = readdir(directory);
            while (entry != NULL) {
                if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {
                    void* child = javan_path_resolve(source_root, (void*) entry->d_name);
                    void** javan_directory_child_roots[] = {
                        (void**) &child
                    };
                    javan_root_frame_push(javan_directory_child_roots, 1);
                    javan_directory_stream_insert_sorted(result, child);
                    javan_root_frame_pop(javan_directory_child_roots);
                }
                entry = readdir(directory);
            }
            javan_native_resource_pop(&directory_resource);
            if (closedir(directory) != 0) {
                javan_panic("newDirectoryStream close failed");
            }
            javan_root_frame_pop(javan_directory_result_roots);
            return result_root;
        }

        static javan_optional* javan_optional_checked(void* value) {
            if (value == NULL) {
                javan_panic("null optional");
            }
            javan_optional* optional = (javan_optional*) value;
            if (optional->magic != JAVAN_OPTIONAL_MAGIC) {
                javan_panic("unsupported optional object");
            }
            return optional;
        }

        static void* javan_optional_new(void* value, int present) {
            void* value_root = value;
            void** javan_optional_value_roots[] = {
                (void**) &value_root
            };
            javan_root_frame_push(javan_optional_value_roots, 1);
            javan_optional* optional = (javan_optional*) javan_alloc(sizeof(javan_optional));
            optional->magic = JAVAN_OPTIONAL_MAGIC;
            optional->present = present;
            optional->reserved0 = 0;
            optional->reserved1 = 0;
            optional->value = present == 0 ? NULL : value_root;
            javan_update_runtime_allocation_kind((void*) optional, JAVAN_RUNTIME_KIND_OPTIONAL);
            javan_root_frame_pop(javan_optional_value_roots);
            return optional;
        }

        void* javan_optional_empty(void) {
            return javan_optional_new(NULL, 0);
        }

        void* javan_optional_of(void* value) {
            if (value == NULL) {
                javan_panic("null optional value");
            }
            return javan_optional_new(value, 1);
        }

        void* javan_optional_of_nullable(void* value) {
            return value == NULL ? javan_optional_empty() : javan_optional_of(value);
        }

        int javan_optional_is_present(void* value) {
            return javan_optional_checked(value)->present != 0;
        }

        int javan_optional_is_empty(void* value) {
            return javan_optional_checked(value)->present == 0;
        }

        void* javan_optional_or_else(void* value, void* fallback) {
            javan_optional* optional = javan_optional_checked(value);
            return optional->present == 0 ? fallback : optional->value;
        }

        void* javan_optional_or_else_throw(void* value) {
            javan_optional* optional = javan_optional_checked(value);
            if (optional->present == 0) {
                javan_panic("optional is empty");
            }
            return optional->value;
        }

        void* javan_string_value_of_int(int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_long(long long value) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_float(float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_bool(int value) {
            return javan_string_copy(value == 0 ? "false" : "true");
        }

        void* javan_string_value_of_char(int value) {
            char buffer[2];
            buffer[0] = (char) value;
            buffer[1] = '\\0';
            return javan_string_copy(buffer);
        }

        void* javan_string_concat(const char* recipe, int argc, const char** values) {
            if (recipe == NULL || argc < 0 || values == NULL) {
                javan_panic("invalid string concat");
            }
            void* javan_concat_values[argc > 0 ? argc : 1];
            void** javan_concat_roots[argc > 0 ? argc : 1];
            for (int index = 0; index < argc; index++) {
                javan_concat_values[index] = (void*) values[index];
                javan_concat_roots[index] = (void**) &javan_concat_values[index];
            }
            unsigned long length = 0;
            int arg = 0;
            for (const unsigned char* cursor = (const unsigned char*) recipe; *cursor != '\\0'; cursor++) {
                if (*cursor == 1) {
                    if (arg >= argc) {
                        javan_panic("invalid string concat argument");
                    }
                    length += strlen(javan_concat_values[arg] == NULL ? "null" : (const char*) javan_concat_values[arg]);
                    arg++;
                } else if (*cursor == 2) {
                    javan_panic("unsupported string concat constant");
                } else {
                    length++;
                }
            }
            if (arg != argc) {
                javan_panic("invalid string concat argument count");
            }
            if (argc > 0) {
                javan_root_frame_push(javan_concat_roots, argc);
            }
            char* result = javan_string_alloc(length + 1);
            char* out = result;
            arg = 0;
            for (const unsigned char* cursor = (const unsigned char*) recipe; *cursor != '\\0'; cursor++) {
                if (*cursor == 1) {
                    const char* value = javan_concat_values[arg] == NULL ? "null" : (const char*) javan_concat_values[arg];
                    unsigned long value_length = strlen(value);
                    memcpy(out, value, value_length);
                    out += value_length;
                    arg++;
                } else {
                    *out = (char) *cursor;
                    out++;
                }
            }
            *out = '\\0';
            if (argc > 0) {
                javan_root_frame_pop(javan_concat_roots);
            }
            return result;
        }

        char* javan_string_export(const char* value) {
            const char* source = value == NULL ? "" : value;
            unsigned long length = strlen(source);
            void* source_root = (void*) source;
            void** javan_string_export_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_export_roots, 1);
            char* result = (char*) javan_export_alloc(length + 1);
            memcpy(result, (const char*) source_root, length + 1);
            javan_root_frame_pop(javan_string_export_roots);
            return result;
        }

        int javan_lcmp(long long left, long long right) {
            if (left > right) {
                return 1;
            }
            if (left == right) {
                return 0;
            }
            return -1;
        }

        int javan_float_compare(float left, float right, int nan_value) {
            if (isnan(left) || isnan(right)) {
                return nan_value;
            }
            if (left > right) {
                return 1;
            }
            if (left == right) {
                return 0;
            }
            return -1;
        }

        int javan_double_compare(double left, double right, int nan_value) {
            if (isnan(left) || isnan(right)) {
                return nan_value;
            }
            if (left > right) {
                return 1;
            }
            if (left == right) {
                return 0;
            }
            return -1;
        }

        void javan_panic(const char* value) {
            const char* message = value == NULL ? "javan panic" : value;
            JavanSourceContext* context = javan_source_context_top;
            if (context != NULL) {
                javan_panic_at(
                    context->code,
                    context->summary,
                    context->class_name,
                    context->method,
                    context->file,
                    context->line,
                    context->bytecode_offset,
                    context->source_line,
                    context->why,
                    context->fix,
                    message
                );
                return;
            }
            javan_record_error(message);
            jmp_buf* target = javan_panic_target;
            if (target == NULL) {
                fputs(message, stderr);
                fputc('\\n', stderr);
            }
            javan_source_context_top = NULL;
            javan_native_resource_cleanup_all();
            javan_root_frame_cleanup();
            if (target != NULL) {
                javan_panic_target = NULL;
                longjmp(*target, 1);
            }
            exit(1);
        }

        void javan_panic_at(
            const char* code,
            const char* summary,
            const char* class_name,
            const char* method,
            const char* file,
            int line,
            int bytecode_offset,
            const char* source_line,
            const char* why,
            const char* fix,
            const char* detail
        ) {
            const char* safe_code = javan_safe_text(code, "JAVAN-RUNTIME-PANIC");
            const char* safe_summary = javan_safe_text(summary, "native runtime panic");
            const char* safe_class = javan_safe_text(class_name, "unknown");
            const char* safe_method = javan_safe_text(method, "unknown");
            const char* safe_file = javan_safe_text(file, "unknown source");
            const char* safe_source_line = source_line == NULL ? "" : source_line;
            const char* safe_why = javan_safe_text(why, "A generated runtime failure was reached.");
            const char* safe_fix = javan_safe_text(fix, "Inspect the reachable Java code that triggered this failure.");
            const char* safe_detail = javan_safe_text(detail, "javan panic");
            javan_record_error_at(
                safe_code,
                safe_summary,
                safe_class,
                safe_method,
                safe_file,
                line,
                bytecode_offset,
                safe_source_line,
                safe_why,
                safe_fix,
                safe_detail
            );
            jmp_buf* target = javan_panic_target;
            if (target == NULL) {
                fprintf(stderr, "[%s] %s\\n\\n", safe_code, safe_summary);
                fprintf(stderr, "Where:\\n");
                if (line >= 0) {
                    fprintf(stderr, "  %s.%s(%s:%d)\\n", safe_class, safe_method, safe_file, line);
                } else {
                    fprintf(stderr, "  %s.%s(%s)\\n", safe_class, safe_method, safe_file);
                }
                fprintf(stderr, "  bytecode offset: %d\\n\\n", bytecode_offset);
                javan_print_source_code(safe_source_line);
                fprintf(stderr, "Why:\\n");
                fprintf(stderr, "  %s\\n", safe_why);
                fprintf(stderr, "  detail: %s\\n\\n", safe_detail);
                fprintf(stderr, "Fix:\\n");
                fprintf(stderr, "  %s\\n", safe_fix);
                fflush(stderr);
            }
            javan_source_context_top = NULL;
            javan_native_resource_cleanup_all();
            javan_root_frame_cleanup();
            if (target != NULL) {
                javan_panic_target = NULL;
                longjmp(*target, 1);
            }
            exit(1);
        }
        """;

    private static final String SOURCE = source();

    private static String source() {
        return new StringBuilder()
            .append(SOURCE_MAIN)
            .append(SOURCE_HEAP)
            .append(SOURCE_HEAP_ALLOC)
            .append(SOURCE_ARRAYS)
            .append(SOURCE_COLLECTIONS)
            .append(SOURCE_TAIL)
            .append(SOURCE_HTTP)
            .append(SOURCE_FILES)
            .toString();
    }

    /**
     * Writes runtime header and source files.
     *
     * @param generatedDirectory output directory
     * @return runtime C source path
     * @throws IOException when writing fails
     */
    public Path write(final Path generatedDirectory) throws IOException {
        Files2.writeString(generatedDirectory.resolve("javan_runtime.h"), HEADER);
        return Files2.writeString(generatedDirectory.resolve("javan_runtime.c"), SOURCE);
    }
}
