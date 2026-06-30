package javan.codegen;

import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;

final class RuntimeHeaderFile {
    private static final String CONTENT = """
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
        void javan_println_object_value(void* value);
        void javan_eprintln_object_value(void* value);
        void javan_printstream_println_object(void* stream, void* value);
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
        float javan_math_abs_float(float value);
        double javan_math_abs_double(double value);
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
        unsigned long javan_heap_registered_thread_roots(void);
        unsigned long javan_heap_thread_objects(void);
        unsigned long javan_heap_started_threads(void);
        unsigned long javan_heap_completed_threads(void);
        unsigned long javan_heap_active_threads(void);
        unsigned long javan_heap_threads_with_target(void);
        int javan_heap_current_thread_root_present(void);
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
        void* javan_arrays_copy_of_boolean(void* array, int new_length);
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
        int javan_string_starts_with_from(const char* left, const char* prefix, int from_index);
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
        void* javan_map_remove(void* map, void* key);
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
        void* javan_thread_new_virtual(void);
        void* javan_virtual_thread_builder_new(void);
        void* javan_virtual_thread_builder_name(void* value, void* name);
        void* javan_virtual_thread_builder_name_counter(void* value, void* prefix, long long start);
        void* javan_virtual_thread_builder_factory(void* value);
        void* javan_virtual_thread_builder_start(void* value, void* runnable);
        void* javan_virtual_thread_builder_unstarted(void* value, void* runnable);
        void* javan_virtual_thread_factory_new_thread(void* value, void* runnable);
        void* javan_virtual_thread_executor_new(void);
        void* javan_virtual_thread_executor_from_factory(void* value);
        void javan_virtual_thread_executor_execute(void* value, void* runnable);
        void javan_virtual_thread_executor_shutdown(void* value);
        void javan_virtual_thread_executor_close(void* value);
        void* javan_virtual_thread_builder_to_string(void* value);
        void* javan_virtual_thread_factory_to_string(void* value);
        void* javan_virtual_thread_executor_to_string(void* value);
        void* javan_virtual_thread_builder_get_class(void* value);
        void* javan_virtual_thread_factory_get_class(void* value);
        void* javan_virtual_thread_executor_get_class(void* value);
        void* javan_runtime_class_get_name(void* value);
        int javan_virtual_thread_object_equals(void* left, void* right);
        int javan_virtual_thread_object_hash_code(void* value);
        void* javan_thread_current(void);
        void* javan_thread_get_name(void* value);
        void javan_thread_set_name(void* value, void* name);
        void javan_thread_detach_current(void);
        void javan_thread_set_target(void* value, void* target);
        void* javan_thread_local_new(void);
        void* javan_thread_local_get(void* value);
        void javan_thread_local_set(void* value, void* thread_local_value);
        void javan_thread_local_remove(void* value);
        void javan_thread_sleep_millis(long long millis);
        int javan_thread_sleep_millis_interruptible(long long millis);
        int javan_thread_interrupted(void);
        void javan_thread_interrupt(void* value);
        int javan_thread_is_interrupted(void* value);
        int javan_thread_is_alive(void* value);
        int javan_thread_is_virtual(void* value);
        void javan_thread_run_target(void* target);
        void javan_thread_start(void* value);
        void javan_thread_join(void* value);
        int javan_thread_join_interruptible(void* value);
        void javan_thread_park(void);
        void javan_thread_park_nanos(long long nanos);
        void javan_thread_park_until(long long deadline_millis);
        void javan_thread_unpark(void* value);
        void javan_wait_for_non_current_threads(void);
        void javan_runtime_profile_consume_args(int* argc, char*** argv);
        void* javan_string_value_of_int(int value);
        void* javan_string_value_of_long(long long value);
        void* javan_string_value_of_float(float value);
        void* javan_string_value_of_double(double value);
        void* javan_string_value_of_bool(int value);
        void* javan_string_value_of_char(int value);
        void* javan_printable_object_string(void* value);
        void* javan_string_concat(const char* recipe, int argc, const char** values);
        char* javan_string_export(const char* value);
        void* javan_stringbuilder_new(void);
        void* javan_stringbuilder_append_string(void* builder, void* value);
        void* javan_stringbuilder_append_object(void* builder, void* value);
        void* javan_stringbuilder_append_boolean(void* builder, int value);
        void* javan_stringbuilder_append_char(void* builder, int value);
        void* javan_stringbuilder_append_int(void* builder, int value);
        void* javan_stringbuilder_append_long(void* builder, long long value);
        void* javan_stringbuilder_append_float(void* builder, float value);
        void* javan_stringbuilder_append_double(void* builder, double value);
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

    private RuntimeHeaderFile() {
    }

    static Path writeTo(final Path generatedDirectory) throws IOException {
        return Files2.writeString(generatedDirectory.resolve("javan_runtime.h"), CONTENT);
    }
}
