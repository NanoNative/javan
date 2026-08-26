package javan.codegen;

final class RuntimeSourcePlatformSection {
    private static final String SOURCE_TAIL_A = """
        static javan_string_builder* javan_stringbuilder_checked(void* value) {
            if (value == NULL) {
                javan_panic("null string builder");
            }
            javan_string_builder* builder = (javan_string_builder*) value;
            if (builder->magic != JAVAN_STRING_BUILDER_MAGIC) {
                javan_panic("unsupported string builder object");
            }
            javan_utf16_cursor_cache_invalidate(builder->values);
            return builder;
        }

        static int javan_stringbuilder_grown_capacity(int current, int required) {
            if (required < 0 || required == INT_MAX) {
                javan_panic("string builder length overflow");
            }
            int next = current > 0 ? current : 16;
            while (next < required) {
                if (next > (INT_MAX - 2) / 2) {
                    javan_panic("string builder length overflow");
                }
                next = next * 2 + 2;
            }
            return next;
        }

        static void javan_stringbuilder_ensure_capacity(
            javan_string_builder* builder,
            int required_capacity,
            int required_bytes
        ) {
            if (required_capacity < 0 || required_bytes < 0) {
                javan_panic("string builder length overflow");
            }
            if (builder->values != NULL
                && required_capacity <= builder->capacity
                && required_bytes <= builder->byte_capacity) {
                return;
            }
            int next_capacity = javan_stringbuilder_grown_capacity(builder->capacity, required_capacity);
            int next_byte_capacity = javan_stringbuilder_grown_capacity(builder->byte_capacity, required_bytes);
            int old_byte_capacity = builder->byte_capacity;
            int created_buffer = builder->values == NULL;
            void** javan_builder_growth_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_growth_roots, 1);
            char* next = (char*) javan_realloc_owned_buffer(
                builder->values,
                (unsigned long) next_byte_capacity + 1UL
            );
            builder->values = next;
            builder->capacity = next_capacity;
            builder->byte_capacity = next_byte_capacity;
            if (created_buffer != 0) {
                javan_update_runtime_allocation_kind((void*) next, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
            } else {
                javan_heap_maybe_validate();
            }
            if (next == NULL) {
                javan_panic("out of memory");
            }
            if (created_buffer != 0) {
                memset(next, 0, (unsigned long) next_byte_capacity + 1UL);
            } else if (next_byte_capacity > old_byte_capacity) {
                memset(next + old_byte_capacity + 1, 0, (unsigned long) (next_byte_capacity - old_byte_capacity));
            }
            javan_root_frame_pop(javan_builder_growth_roots);
        }

        void javan_stringbuilder_reserve_for_string(void* builder_value, void* string_value) {
            const char* text = string_value == NULL ? "null" : (const char*) string_value;
            int reserve = javan_string_length(text);
            if (reserve > INT_MAX - 16) {
                javan_panic("string builder length overflow");
            }
            reserve += 16;
            javan_stringbuilder_reserve(builder_value, reserve);
        }

        static int javan_stringbuilder_normalized_byte_length(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            const unsigned char* current = (const unsigned char*) value;
            int byte_length = 0;
            while (*current != 0U) {
                unsigned int code_point = javan_utf8_next_code_point(&current, 1);
                int char_length = code_point <= 0xFFFFU
                    ? javan_modified_utf8_char_length((int) code_point)
                    : 6;
                if (byte_length > INT_MAX - char_length) {
                    javan_panic("string builder length overflow");
                }
                byte_length += char_length;
            }
            return byte_length;
        }

        static int javan_stringbuilder_ascii_length(const char* value) {
            int length = 0;
            while (value[length] != '\\0') {
                if (((unsigned char) value[length]) > 0x7FU) {
                    return -1;
                }
                if (length == INT_MAX) {
                    javan_panic("string builder length overflow");
                }
                length++;
            }
            return length;
        }

        static char* javan_stringbuilder_write_normalized(char* output, const char* value) {
            const unsigned char* current = (const unsigned char*) value;
            while (*current != 0U) {
                unsigned int code_point = javan_utf8_next_code_point(&current, 1);
                if (code_point <= 0xFFFFU) {
                    output = javan_modified_utf8_write_char(output, (int) code_point);
                    continue;
                }
                unsigned int adjusted = code_point - 0x10000U;
                output = javan_modified_utf8_write_char(output, (int) (0xD800U + (adjusted >> 10)));
                output = javan_modified_utf8_write_char(output, (int) (0xDC00U + (adjusted & 0x3FFU)));
            }
            return output;
        }

        static int javan_stringbuilder_byte_offset(javan_string_builder* builder, int index) {
            if (index < 0 || index > builder->length) {
                javan_panic("string builder index out of bounds");
            }
            const unsigned char* current = (const unsigned char*) builder->values;
            int current_index = 0;
            while (*current != 0U) {
                if (current_index == index) {
                    return (int) (current - (const unsigned char*) builder->values);
                }
                unsigned int code_point = javan_utf8_next_code_point(&current, 1);
                current_index += code_point > 0xFFFFU ? 2 : 1;
            }
            if (current_index == index) {
                return (int) (current - (const unsigned char*) builder->values);
            }
            javan_panic("invalid string builder encoding");
            return 0;
        }

        static void javan_stringbuilder_append_string_value(javan_string_builder* builder, const char* value) {
            const char* source = value == NULL ? "null" : value;
            int ascii_length = javan_stringbuilder_ascii_length(source);
            int source_length = ascii_length >= 0 ? ascii_length : javan_string_length(source);
            int source_bytes = ascii_length >= 0 ? ascii_length : javan_stringbuilder_normalized_byte_length(source);
            int builder_bytes = builder->byte_length;
            if (builder->length > INT_MAX - source_length || builder_bytes > INT_MAX - source_bytes) {
                javan_panic("string builder length overflow");
            }
            void* source_root = (void*) source;
            void** javan_builder_append_roots[] = {
                (void**) &builder,
                (void**) &source_root
            };
            javan_root_frame_push(javan_builder_append_roots, 2);
            javan_stringbuilder_ensure_capacity(
                builder,
                builder->length + source_length,
                builder_bytes + source_bytes
            );
            char* out = builder->values + builder_bytes;
            if (ascii_length >= 0) {
                memcpy(out, (const char*) source_root, (unsigned long) source_bytes);
                out += source_bytes;
            } else {
                out = javan_stringbuilder_write_normalized(out, (const char*) source_root);
            }
            builder->length += source_length;
            builder->byte_length += source_bytes;
            *out = '\\0';
            javan_root_frame_pop(javan_builder_append_roots);
        }

        static void javan_stringbuilder_append_char_value(javan_string_builder* builder, int value) {
            int builder_bytes = builder->byte_length;
            int char_bytes = javan_modified_utf8_char_length(value);
            if (builder->length == INT_MAX || builder_bytes > INT_MAX - char_bytes) {
                javan_panic("string builder length overflow");
            }
            void** roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(roots, 1);
            javan_stringbuilder_ensure_capacity(builder, builder->length + 1, builder_bytes + char_bytes);
            char* end = javan_modified_utf8_write_char(builder->values + builder_bytes, value);
            builder->length++;
            builder->byte_length += char_bytes;
            *end = '\\0';
            javan_root_frame_pop(roots);
        }

        static void javan_stringbuilder_insert_string_value(javan_string_builder* builder, int index, const char* value) {
            if (index < 0 || index > builder->length) {
                javan_panic("string builder insert index out of bounds");
            }
            const char* source = value == NULL ? "null" : value;
            int ascii_length = javan_stringbuilder_ascii_length(source);
            int source_length = ascii_length >= 0 ? ascii_length : javan_string_length(source);
            int source_bytes = ascii_length >= 0 ? ascii_length : javan_stringbuilder_normalized_byte_length(source);
            int builder_bytes = builder->byte_length;
            if (builder->length > INT_MAX - source_length || builder_bytes > INT_MAX - source_bytes) {
                javan_panic("string builder length overflow");
            }
            void* source_root = (void*) source;
            void** javan_builder_insert_roots[] = {
                (void**) &builder,
                (void**) &source_root
            };
            javan_root_frame_push(javan_builder_insert_roots, 2);
            int byte_index = javan_stringbuilder_byte_offset(builder, index);
            javan_stringbuilder_ensure_capacity(
                builder,
                builder->length + source_length,
                builder_bytes + source_bytes
            );
            memmove(
                builder->values + byte_index + source_bytes,
                builder->values + byte_index,
                (unsigned long) (builder_bytes - byte_index + 1)
            );
            char* out = builder->values + byte_index;
            if (ascii_length >= 0) {
                memcpy(out, (const char*) source_root, (unsigned long) source_bytes);
            } else {
                (void) javan_stringbuilder_write_normalized(out, (const char*) source_root);
            }
            builder->length += source_length;
            builder->byte_length += source_bytes;
            javan_root_frame_pop(javan_builder_insert_roots);
        }

        static void javan_stringbuilder_insert_char_value(javan_string_builder* builder, int index, int value) {
            if (index < 0 || index > builder->length) {
                javan_panic("string builder insert index out of bounds");
            }
            int builder_bytes = builder->byte_length;
            int char_bytes = javan_modified_utf8_char_length(value);
            if (builder->length == INT_MAX || builder_bytes > INT_MAX - char_bytes) {
                javan_panic("string builder length overflow");
            }
            int byte_index = javan_stringbuilder_byte_offset(builder, index);
            void** roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(roots, 1);
            javan_stringbuilder_ensure_capacity(builder, builder->length + 1, builder_bytes + char_bytes);
            memmove(
                builder->values + byte_index + char_bytes,
                builder->values + byte_index,
                (unsigned long) (builder_bytes - byte_index + 1)
            );
            (void) javan_modified_utf8_write_char(builder->values + byte_index, value);
            builder->length++;
            builder->byte_length += char_bytes;
            javan_root_frame_pop(roots);
        }

        void* javan_stringbuilder_new(void) {
            javan_string_builder* builder = (javan_string_builder*) javan_alloc(sizeof(javan_string_builder));
            builder->magic = JAVAN_STRING_BUILDER_MAGIC;
            builder->length = 0;
            builder->byte_length = 0;
            builder->capacity = 16;
            builder->byte_capacity = 16;
            builder->values = NULL;
            javan_update_runtime_allocation_kind((void*) builder, JAVAN_RUNTIME_KIND_STRING_BUILDER);
            void** javan_builder_owner_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_owner_roots, 1);
            javan_stringbuilder_ensure_capacity(builder, 0, 0);
            javan_root_frame_pop(javan_builder_owner_roots);
            builder->values[0] = '\\0';
            return builder;
        }

        void javan_stringbuilder_reserve(void* builder_value, int capacity) {
            if (capacity < 0) {
                javan_panic("negative string builder capacity");
            }
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (capacity <= builder->capacity) {
                return;
            }
            void** javan_builder_owner_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_owner_roots, 1);
            int next_byte_capacity = builder->byte_capacity;
            if (next_byte_capacity < capacity) {
                next_byte_capacity = capacity;
            }
            char* next = (char*) javan_realloc_owned_buffer(
                builder->values,
                (unsigned long) next_byte_capacity + 1UL
            );
            if (next == NULL) {
                javan_panic("out of memory");
            }
            if (next_byte_capacity > builder->byte_capacity) {
                memset(
                    next + builder->byte_capacity + 1,
                    0,
                    (unsigned long) (next_byte_capacity - builder->byte_capacity)
                );
            }
            builder->values = next;
            builder->capacity = capacity;
            builder->byte_capacity = next_byte_capacity;
            javan_heap_maybe_validate();
            javan_root_frame_pop(javan_builder_owner_roots);
        }

        void* javan_stringbuilder_append_string(void* builder_value, void* value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_append_string_value(builder, (const char*) value);
            return builder;
        }

        void* javan_stringbuilder_append_chars_range(void* builder_value, void* chars_value, int offset, int count) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void* chars = chars_value;
            void** javan_builder_append_chars_roots[] = {
                (void**) &builder,
                (void**) &chars
            };
            javan_root_frame_push(javan_builder_append_chars_roots, 2);
            void* text = javan_string_from_chars(chars, offset, count);
            javan_stringbuilder_append_string_value(builder, (const char*) text);
            javan_root_frame_pop(javan_builder_append_chars_roots);
            return builder_value;
        }

        void* javan_stringbuilder_append_chars(void* builder_value, void* chars_value) {
            return javan_stringbuilder_append_chars_range(builder_value, chars_value, 0, javan_array_length(chars_value));
        }

        void* javan_stringbuilder_append_object(void* builder_value, void* value) {
            return javan_stringbuilder_append_string(builder_value, value);
        }

        static int javan_virtual_thread_identity_hash(void* value) {
            if (value == NULL) {
                javan_panic("null virtual thread runtime object");
            }
            uintptr_t bits = (uintptr_t) value;
            unsigned int hash = (unsigned int) (bits ^ (bits >> 32));
            if (hash == 0U) {
                hash = 1U;
            }
            return (int) hash;
        }

        static void* javan_virtual_thread_default_to_string(void* value, const char* class_name) {
            char buffer[160];
            snprintf(
                buffer,
                sizeof(buffer),
                "%s@%x",
                class_name == NULL ? "java.lang.Object" : class_name,
                (unsigned int) javan_virtual_thread_identity_hash(value)
            );
            return javan_string_from(buffer);
        }

        void* javan_virtual_thread_builder_to_string(void* value) {
            javan_virtual_thread_builder_checked(value);
            return javan_virtual_thread_default_to_string(value, "java.lang.ThreadBuilders$VirtualThreadBuilder");
        }

        void* javan_virtual_thread_factory_to_string(void* value) {
            javan_virtual_thread_factory_checked(value);
            return javan_virtual_thread_default_to_string(value, "java.lang.ThreadBuilders$VirtualThreadFactory");
        }

        void* javan_virtual_thread_executor_to_string(void* value) {
            javan_virtual_thread_executor_checked(value);
            return javan_virtual_thread_default_to_string(value, "java.util.concurrent.ThreadPerTaskExecutor");
        }

        int javan_virtual_thread_object_equals(void* left, void* right) {
            return left == right;
        }

        int javan_virtual_thread_object_hash_code(void* value) {
            return javan_virtual_thread_identity_hash(value);
        }

        void* javan_stringbuilder_append_boolean(void* builder_value, int value) {
            javan_stringbuilder_append_string_value(javan_stringbuilder_checked(builder_value), value == 0 ? "false" : "true");
            return builder_value;
        }

        void* javan_stringbuilder_append_char(void* builder_value, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_append_char_value(builder, value);
            return builder;
        }

        void* javan_stringbuilder_append_int(void* builder_value, int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            javan_stringbuilder_append_string_value(javan_stringbuilder_checked(builder_value), buffer);
            return builder_value;
        }

        void* javan_stringbuilder_append_long(void* builder_value, long long value) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            javan_stringbuilder_append_string_value(javan_stringbuilder_checked(builder_value), buffer);
            return builder_value;
        }

        void* javan_stringbuilder_append_float(void* builder_value, float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            javan_stringbuilder_append_string_value(javan_stringbuilder_checked(builder_value), buffer);
            return builder_value;
        }

        void* javan_stringbuilder_append_double(void* builder_value, double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            javan_stringbuilder_append_string_value(javan_stringbuilder_checked(builder_value), buffer);
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

        int javan_stringbuilder_char_at(void* builder_value, int index) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (index < 0 || index >= builder->length) {
                javan_panic("string builder index out of bounds");
            }
            return javan_string_char_at(builder->values, index);
        }

        int javan_char_sequence_length(void* value) {
            if (value == NULL) {
                javan_panic("null CharSequence");
            }
            javan_allocation_metadata snapshot;
            if (javan_find_allocation(value, &snapshot) == 0
                || snapshot.runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return javan_string_length((const char*) value);
            }
            if (snapshot.runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_length(value);
            }
            javan_panic("unsupported CharSequence runtime");
            return 0;
        }

        int javan_char_sequence_char_at(void* value, int index) {
            if (value == NULL) {
                javan_panic("null CharSequence");
            }
            javan_allocation_metadata snapshot;
            if (javan_find_allocation(value, &snapshot) == 0
                || snapshot.runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return javan_string_char_at((const char*) value, index);
            }
            if (snapshot.runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_char_at(value, index);
            }
            javan_panic("unsupported CharSequence runtime");
            return 0;
        }

        int javan_character_is_whitespace(int value) {
            return value == 0x20
                || (value >= 0x09 && value <= 0x0d)
                || (value >= 0x1c && value <= 0x1f)
                || value == 0x1680
                || (value >= 0x2000 && value <= 0x2006)
                || (value >= 0x2008 && value <= 0x200a)
                || value == 0x2028
                || value == 0x2029
                || value == 0x205f
                || value == 0x3000;
        }

        void* javan_stringbuilder_substring(void* builder_value, int begin) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void** javan_builder_substring_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_substring_roots, 1);
            void* result = javan_string_substring(builder->values, begin);
            javan_root_frame_pop(javan_builder_substring_roots);
            return result;
        }

        void* javan_stringbuilder_substring_range(void* builder_value, int begin, int end) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void** javan_builder_substring_range_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_substring_range_roots, 1);
            void* result = javan_string_substring_range(builder->values, begin, end);
            javan_root_frame_pop(javan_builder_substring_range_roots);
            return result;
        }

        int javan_stringbuilder_index_of_string(void* builder_value, void* needle_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            return javan_string_index_of_string(builder->values, (const char*) needle_value);
        }

        int javan_stringbuilder_index_of_string_from(void* builder_value, void* needle_value, int from_index) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            return javan_string_index_of_string_from(builder->values, (const char*) needle_value, from_index);
        }

        int javan_stringbuilder_last_index_of_string(void* builder_value, void* needle_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            return javan_string_last_index_of_string(builder->values, (const char*) needle_value);
        }

        int javan_stringbuilder_last_index_of_string_from(void* builder_value, void* needle_value, int from_index) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            return javan_string_last_index_of_string_from(builder->values, (const char*) needle_value, from_index);
        }

        int javan_stringbuilder_compare_to(void* builder_value, void* other_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_string_builder* other = javan_stringbuilder_checked(other_value);
            int left_length = builder->length;
            int right_length = other->length;
            int shared = left_length < right_length ? left_length : right_length;
            for (int index = 0; index < shared; index++) {
                int left = javan_string_char_at(builder->values, index);
                int right = javan_string_char_at(other->values, index);
                if (left != right) {
                    return left - right;
                }
            }
            return left_length - right_length;
        }

        void* javan_stringbuilder_delete(void* builder_value, int start, int end) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (start < 0 || start > builder->length) {
                javan_panic("string builder delete range out of bounds");
            }
            if (end < start) {
                javan_panic("string builder delete start greater than end");
            }
            int effective_end = end > builder->length ? builder->length : end;
            if (effective_end == start) {
                return builder_value;
            }
            int byte_start = javan_stringbuilder_byte_offset(builder, start);
            int byte_end = javan_stringbuilder_byte_offset(builder, effective_end);
            int byte_length = builder->byte_length;
            memmove(
                builder->values + byte_start,
                builder->values + byte_end,
                (unsigned long) (byte_length - byte_end + 1)
            );
            builder->length -= effective_end - start;
            builder->byte_length -= byte_end - byte_start;
            return builder_value;
        }

        void* javan_stringbuilder_delete_char_at(void* builder_value, int index) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (index < 0 || index >= builder->length) {
                javan_panic("string builder delete char index out of bounds");
            }
            return javan_stringbuilder_delete(builder_value, index, index + 1);
        }

        void* javan_stringbuilder_insert_boolean(void* builder_value, int index, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_insert_string_value(builder, index, value == 0 ? "false" : "true");
            return builder_value;
        }

        void* javan_stringbuilder_insert_int(void* builder_value, int index, int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            javan_stringbuilder_insert_string_value(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_long(void* builder_value, int index, long long value) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            javan_stringbuilder_insert_string_value(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_float(void* builder_value, int index, float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            javan_stringbuilder_insert_string_value(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_double(void* builder_value, int index, double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            javan_stringbuilder_insert_string_value(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_chars_range(void* builder_value, int index, void* chars_value, int offset, int count) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void* chars = chars_value;
            void** javan_builder_insert_chars_roots[] = {
                (void**) &builder,
                (void**) &chars
            };
            javan_root_frame_push(javan_builder_insert_chars_roots, 2);
            void* text = javan_string_from_chars(chars, offset, count);
            javan_stringbuilder_insert_string_value(builder, index, (const char*) text);
            javan_root_frame_pop(javan_builder_insert_chars_roots);
            return builder_value;
        }

        void* javan_stringbuilder_insert_chars(void* builder_value, int index, void* chars_value) {
            return javan_stringbuilder_insert_chars_range(builder_value, index, chars_value, 0, javan_array_length(chars_value));
        }

        void* javan_stringbuilder_insert_string(void* builder_value, int index, void* value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_insert_string_value(builder, index, (const char*) value);
            return builder_value;
        }

        void* javan_stringbuilder_insert_char(void* builder_value, int index, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_insert_char_value(builder, index, value);
            return builder_value;
        }

        void* javan_stringbuilder_replace_string(void* builder_value, int start, int end, void* value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (start < 0 || start > builder->length) {
                javan_panic("string builder replace range out of bounds");
            }
            if (end < start) {
                javan_panic("string builder replace start greater than end");
            }
            int effective_end = end > builder->length ? builder->length : end;
            javan_stringbuilder_delete(builder_value, start, effective_end);
            javan_stringbuilder_insert_string(builder_value, start, value);
            return builder_value;
        }

        void* javan_stringbuilder_reverse(void* builder_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            int source_length = builder->length;
            void* source = NULL;
            void** roots[] = {
                (void**) &builder,
                (void**) &source
            };
            javan_root_frame_push(roots, 2);
            source = javan_string_copy(builder->values);
            builder->length = 0;
            builder->byte_length = 0;
            builder->values[0] = '\\0';
            for (int index = source_length - 1; index >= 0; index--) {
                int value = javan_string_char_at((const char*) source, index);
                if (value >= 0xDC00 && value <= 0xDFFF
                    && index > 0) {
                    int previous = javan_string_char_at((const char*) source, index - 1);
                    if (previous >= 0xD800 && previous <= 0xDBFF) {
                        javan_stringbuilder_append_char(builder, previous);
                        javan_stringbuilder_append_char(builder, value);
                        index--;
                        continue;
                    }
                }
                javan_stringbuilder_append_char(builder, value);
            }
            javan_root_frame_pop(roots);
            return builder_value;
        }

        void javan_stringbuilder_ensure_capacity_public(void* builder_value, int minimum_capacity) {
            if (minimum_capacity <= 0) {
                return;
            }
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            void** javan_builder_ensure_capacity_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_ensure_capacity_roots, 1);
            javan_stringbuilder_ensure_capacity(builder, minimum_capacity, builder->byte_length);
            javan_root_frame_pop(javan_builder_ensure_capacity_roots);
        }

        void javan_stringbuilder_trim_to_size(void* builder_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            int target_capacity = builder->length;
            int target_byte_capacity = builder->byte_length;
            if (builder->capacity == target_capacity && builder->byte_capacity == target_byte_capacity) {
                return;
            }
            void** javan_builder_trim_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_trim_roots, 1);
            char* next = (char*) javan_realloc_owned_buffer(
                builder->values,
                (unsigned long) target_byte_capacity + 1UL
            );
            if (next == NULL) {
                javan_panic("out of memory");
            }
            builder->values = next;
            builder->capacity = target_capacity;
            builder->byte_capacity = target_byte_capacity;
            builder->values[target_byte_capacity] = '\\0';
            javan_heap_maybe_validate();
            javan_root_frame_pop(javan_builder_trim_roots);
        }

        void javan_stringbuilder_set_char_at(void* builder_value, int index, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (index < 0 || index >= builder->length) {
                javan_panic("string builder set char index out of bounds");
            }
            javan_stringbuilder_delete_char_at(builder_value, index);
            javan_stringbuilder_insert_char(builder_value, index, value);
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
            int current_length = builder->length;
            int current_bytes = builder->byte_length;
            if (length < current_length) {
                int byte_offset = javan_stringbuilder_byte_offset(builder, length);
                builder->values[byte_offset] = '\\0';
                builder->length = length;
                builder->byte_length = byte_offset;
                javan_root_frame_pop(javan_builder_set_length_roots);
                return;
            }
            int added = length - current_length;
            if (added > (INT_MAX - current_bytes) / 2) {
                javan_panic("string builder length overflow");
            }
            javan_stringbuilder_ensure_capacity(builder, length, current_bytes + (added * 2));
            char* out = builder->values + current_bytes;
            for (int index = 0; index < added; index++) {
                out = javan_modified_utf8_write_char(out, 0);
            }
            builder->length = length;
            builder->byte_length = current_bytes + (added * 2);
            *out = '\\0';
            javan_root_frame_pop(javan_builder_set_length_roots);
        }

        int javan_stringbuilder_capacity(void* builder_value) {
            return javan_stringbuilder_checked(builder_value)->capacity;
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

        static void* javan_inet_address_new(const char* host_address, const char* host_name, const char* canonical_host_name) {
            const char* address_value = host_address == NULL ? "0.0.0.0" : host_address;
            const char* name_value = host_name == NULL ? address_value : host_name;
            const char* canonical_value = canonical_host_name == NULL ? name_value : canonical_host_name;
            void* host_address_copy = NULL;
            void* host_name_copy = NULL;
            void* canonical_host_name_copy = NULL;
            void* object_root = NULL;
            void** javan_inet_address_object_roots[] = {
                (void**) &host_address_copy,
                (void**) &host_name_copy,
                (void**) &canonical_host_name_copy,
                (void**) &object_root
            };
            javan_root_frame_push(javan_inet_address_object_roots, 4);
            host_address_copy = javan_string_copy(address_value);
            host_name_copy = javan_string_copy(name_value);
            canonical_host_name_copy = javan_string_copy(canonical_value);
            javan_inet_address* address = (javan_inet_address*) javan_alloc(sizeof(javan_inet_address));
            object_root = (void*) address;
            address->magic = JAVAN_INET_ADDRESS_MAGIC;
            address->reserved0 = 0;
            address->reserved1 = 0;
            address->reserved2 = 0;
            address->host_address = (char*) host_address_copy;
            address->host_name = (char*) host_name_copy;
            address->canonical_host_name = (char*) canonical_host_name_copy;
            javan_update_runtime_allocation_kind(object_root, JAVAN_RUNTIME_KIND_INET_ADDRESS);
            javan_root_frame_pop(javan_inet_address_object_roots);
            return object_root;
        }

        void* javan_inet_address_loopback(void) {
            return javan_inet_address_new("127.0.0.1", "localhost", "localhost");
        }

        static int javan_inet_address_is_ipv6_loopback(const unsigned char* bytes) {
            int index = 0;
            while (index < 15) {
                if (bytes[index] != 0) {
                    return 0;
                }
                index++;
            }
            return bytes[15] == 1 ? 1 : 0;
        }

        """;

    private static final String SOURCE_TAIL_B = """
        static int javan_socket_getsockopt_int(javan_socket_handle fd, int level, int option_name, const char* message);
        static int javan_socket_getsockopt_buffer_size(javan_socket_handle fd, int option_name, const char* message);
        static const char* javan_socket_inet_ntop(int family, const void* source, char* destination, unsigned long destination_size);
        static javan_socket* javan_socket_checked(void* value);
        static javan_server_socket* javan_server_socket_checked(void* value);

        static void javan_inet_address_format_ipv6(const unsigned char* bytes, char* host_address, unsigned long host_address_size) {
            int written = snprintf(
                host_address,
                host_address_size,
                "%x:%x:%x:%x:%x:%x:%x:%x",
                (((unsigned int) bytes[0]) << 8) | ((unsigned int) bytes[1]),
                (((unsigned int) bytes[2]) << 8) | ((unsigned int) bytes[3]),
                (((unsigned int) bytes[4]) << 8) | ((unsigned int) bytes[5]),
                (((unsigned int) bytes[6]) << 8) | ((unsigned int) bytes[7]),
                (((unsigned int) bytes[8]) << 8) | ((unsigned int) bytes[9]),
                (((unsigned int) bytes[10]) << 8) | ((unsigned int) bytes[11]),
                (((unsigned int) bytes[12]) << 8) | ((unsigned int) bytes[13]),
                (((unsigned int) bytes[14]) << 8) | ((unsigned int) bytes[15])
            );
            if (written < 0 || (unsigned long) written >= host_address_size) {
                javan_panic("inet6 address conversion failed");
            }
        }

        static void javan_inet_address_host_checked(const char* host, char* host_address, unsigned long host_address_size, int* loopback_out) {
            struct sockaddr_in address;
            struct in6_addr address6;
            const char* host_value = host == NULL ? "localhost" : host;
            if (loopback_out != NULL) {
                *loopback_out = 0;
            }
            memset(&address, 0, sizeof(address));
            if (strcmp(host_value, "localhost") == 0) {
                if (loopback_out != NULL) {
                    *loopback_out = 1;
                }
                snprintf(host_address, host_address_size, "%s", "127.0.0.1");
                return;
            }
            address.sin_family = AF_INET;
            if (inet_pton(AF_INET, host_value, &address.sin_addr) == 1) {
                if (javan_socket_inet_ntop(AF_INET, (const void*) &address.sin_addr, host_address, host_address_size) == NULL) {
                    javan_panic("inet address conversion failed");
                }
                if (loopback_out != NULL && strcmp(host_address, "127.0.0.1") == 0) {
                    *loopback_out = 1;
                }
                return;
            }
            memset(&address6, 0, sizeof(address6));
            if (inet_pton(AF_INET6, host_value, &address6) == 1) {
                javan_inet_address_format_ipv6((const unsigned char*) &address6, host_address, host_address_size);
                if (loopback_out != NULL && javan_inet_address_is_ipv6_loopback((const unsigned char*) &address6) != 0) {
                    *loopback_out = 1;
                }
                return;
            }
            javan_panic("unsupported inet address host");
        }

        void* javan_inet_address_get_by_name(void* host) {
            const char* host_value = host == NULL ? "localhost" : (const char*) host;
            char host_address[64];
            int loopback = 0;
            javan_inet_address_host_checked(host_value, host_address, sizeof(host_address), &loopback);
            const char* host_name = loopback != 0 ? "localhost" : host_address;
            return javan_inet_address_new(host_address, host_name, host_name);
        }

        void* javan_inet_address_get_all_by_name(void* host) {
            void* address_root = javan_inet_address_get_by_name(host);
            void* array_root = NULL;
            void** javan_inet_address_all_roots[] = {
                (void**) &address_root,
                (void**) &array_root
            };
            javan_root_frame_push(javan_inet_address_all_roots, 2);
            array_root = javan_object_array_new(1, "[Ljava.net.InetAddress;");
            javan_object_array_set(array_root, 0, address_root);
            javan_root_frame_pop(javan_inet_address_all_roots);
            return array_root;
        }

        void* javan_inet_address_get_by_address(void* bytes) {
            javan_array_header* array = javan_array_checked(bytes);
            if (array->kind != JAVAN_ARRAY_KIND_BYTE) {
                javan_panic("InetAddress.getByAddress requires byte[]");
            }
            javan_byte_array* values = (javan_byte_array*) array;
            char host_address[64];
            const char* host_name = NULL;
            if (values->length == 4) {
                int written = snprintf(
                    host_address,
                    sizeof(host_address),
                    "%u.%u.%u.%u",
                    (unsigned int) ((unsigned char) values->values[0]),
                    (unsigned int) ((unsigned char) values->values[1]),
                    (unsigned int) ((unsigned char) values->values[2]),
                    (unsigned int) ((unsigned char) values->values[3])
                );
                if (written < 0 || (unsigned long) written >= sizeof(host_address)) {
                    javan_panic("inet address conversion failed");
                }
                host_name = strcmp(host_address, "127.0.0.1") == 0 ? "localhost" : host_address;
                return javan_inet_address_new(host_address, host_name, host_name);
            }
            if (values->length == 16) {
                javan_inet_address_format_ipv6((const unsigned char*) values->values, host_address, sizeof(host_address));
                host_name = javan_inet_address_is_ipv6_loopback((const unsigned char*) values->values) != 0 ? "localhost" : host_address;
                return javan_inet_address_new(host_address, host_name, host_name);
            }
            javan_panic("addr is of illegal length");
            return NULL;
        }

        void* javan_inet_address_get_by_address_named(void* host, void* bytes) {
            javan_array_header* array = javan_array_checked(bytes);
            if (array->kind != JAVAN_ARRAY_KIND_BYTE) {
                javan_panic("InetAddress.getByAddress requires byte[]");
            }
            javan_byte_array* values = (javan_byte_array*) array;
            char host_address[64];
            const char* host_name = host == NULL ? NULL : (const char*) host;
            const char* canonical_host_name = NULL;
            if (values->length == 4) {
                int written = snprintf(
                    host_address,
                    sizeof(host_address),
                    "%u.%u.%u.%u",
                    (unsigned int) ((unsigned char) values->values[0]),
                    (unsigned int) ((unsigned char) values->values[1]),
                    (unsigned int) ((unsigned char) values->values[2]),
                    (unsigned int) ((unsigned char) values->values[3])
                );
                if (written < 0 || (unsigned long) written >= sizeof(host_address)) {
                    javan_panic("inet address conversion failed");
                }
                canonical_host_name = strcmp(host_address, "127.0.0.1") == 0 ? "localhost" : host_address;
                if (host_name == NULL) {
                    host_name = canonical_host_name;
                }
                return javan_inet_address_new(host_address, host_name, canonical_host_name);
            }
            if (values->length == 16) {
                javan_inet_address_format_ipv6((const unsigned char*) values->values, host_address, sizeof(host_address));
                canonical_host_name = javan_inet_address_is_ipv6_loopback((const unsigned char*) values->values) != 0 ? "localhost" : host_address;
                if (host_name == NULL) {
                    host_name = canonical_host_name;
                }
                return javan_inet_address_new(host_address, host_name, canonical_host_name);
            }
            javan_panic("addr is of illegal length");
            return NULL;
        }

        void* javan_inet_address_get_address(void* value) {
            const char* host_address = javan_inet_address_checked(value)->host_address;
            struct in_addr address4;
            struct in6_addr address6;
            if (inet_pton(AF_INET, host_address, &address4) == 1) {
                return javan_byte_array_from((const signed char*) &address4, 4);
            }
            if (inet_pton(AF_INET6, host_address, &address6) == 1) {
                return javan_byte_array_from((const signed char*) &address6, 16);
            }
            javan_panic("unsupported inet address host");
            return NULL;
        }

        void* javan_inet_address_get_host_address(void* value) {
            return javan_inet_address_checked(value)->host_address;
        }

        void* javan_inet_address_get_host_name(void* value) {
            return javan_inet_address_checked(value)->host_name;
        }

        void* javan_inet_address_get_canonical_host_name(void* value) {
            return javan_inet_address_checked(value)->canonical_host_name;
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
            void* address = javan_inet_address_new(host_value, host_value, host_value);
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

        """;

    private static final String SOURCE_TAIL_SOCKET = """
        static int javan_socket_handle_is_open(javan_socket_handle fd) {
            return fd != JAVAN_SOCKET_INVALID;
        }

        #if defined(_WIN32)
        static INIT_ONCE javan_socket_runtime_once = INIT_ONCE_STATIC_INIT;
        static int javan_socket_runtime_startup_result = WSASYSNOTREADY;

        static BOOL CALLBACK javan_socket_runtime_startup(PINIT_ONCE once, PVOID parameter, PVOID* context) {
            (void) once;
            (void) parameter;
            (void) context;
            WSADATA data;
            javan_socket_runtime_startup_result = WSAStartup(MAKEWORD(2, 2), &data);
            return TRUE;
        }
        #endif

        static void javan_socket_runtime_initialize(void) {
        #if defined(_WIN32)
            if (InitOnceExecuteOnce(&javan_socket_runtime_once, javan_socket_runtime_startup, NULL, NULL) == 0
                || javan_socket_runtime_startup_result != 0) {
                javan_panic("Winsock startup failed");
            }
        #endif
        }

        static javan_socket_handle javan_socket_native_open(int family) {
            javan_socket_runtime_initialize();
        #if defined(_WIN32)
            return (javan_socket_handle) socket(family, SOCK_STREAM, IPPROTO_TCP);
        #else
            return (javan_socket_handle) socket(family, SOCK_STREAM, 0);
        #endif
        }

        static int javan_socket_native_close(javan_socket_handle fd) {
        #if defined(_WIN32)
            return closesocket((SOCKET) fd);
        #else
            return close((int) fd);
        #endif
        }

        static int javan_socket_native_bind(javan_socket_handle fd, const struct sockaddr* address, javan_socket_length address_length) {
        #if defined(_WIN32)
            return bind((SOCKET) fd, address, address_length);
        #else
            return bind((int) fd, address, address_length);
        #endif
        }

        static int javan_socket_native_connect(javan_socket_handle fd, const struct sockaddr* address, javan_socket_length address_length) {
        #if defined(_WIN32)
            return connect((SOCKET) fd, address, address_length);
        #else
            return connect((int) fd, address, address_length);
        #endif
        }

        static int javan_socket_native_listen(javan_socket_handle fd, int backlog) {
        #if defined(_WIN32)
            return listen((SOCKET) fd, backlog);
        #else
            return listen((int) fd, backlog);
        #endif
        }

        static javan_socket_handle javan_socket_native_accept(javan_socket_handle fd) {
        #if defined(_WIN32)
            return (javan_socket_handle) accept((SOCKET) fd, NULL, NULL);
        #else
            return (javan_socket_handle) accept((int) fd, NULL, NULL);
        #endif
        }

        static int javan_socket_native_get_name(
            javan_socket_handle fd,
            int peer,
            struct sockaddr* address,
            javan_socket_length* address_length
        ) {
        #if defined(_WIN32)
            return peer != 0
                ? getpeername((SOCKET) fd, address, address_length)
                : getsockname((SOCKET) fd, address, address_length);
        #else
            return peer != 0
                ? getpeername((int) fd, address, address_length)
                : getsockname((int) fd, address, address_length);
        #endif
        }

        static int javan_socket_native_get_option(
            javan_socket_handle fd,
            int level,
            int option_name,
            void* value,
            javan_socket_length* length
        ) {
        #if defined(_WIN32)
            return getsockopt((SOCKET) fd, level, option_name, (char*) value, length);
        #else
            return getsockopt((int) fd, level, option_name, value, length);
        #endif
        }

        static int javan_socket_native_set_option(
            javan_socket_handle fd,
            int level,
            int option_name,
            const void* value,
            javan_socket_length length
        ) {
        #if defined(_WIN32)
            return setsockopt((SOCKET) fd, level, option_name, (const char*) value, length);
        #else
            return setsockopt((int) fd, level, option_name, value, length);
        #endif
        }

        static int javan_socket_native_receive(javan_socket_handle fd, void* bytes, int length) {
        #if defined(_WIN32)
            return recv((SOCKET) fd, (char*) bytes, length, 0);
        #else
            return (int) recv((int) fd, bytes, (size_t) length, 0);
        #endif
        }

        static int javan_socket_native_send(javan_socket_handle fd, const void* bytes, int length, int flags) {
        #if defined(_WIN32)
            return send((SOCKET) fd, (const char*) bytes, length, flags);
        #else
            return (int) send((int) fd, bytes, (size_t) length, flags);
        #endif
        }

        static int javan_socket_native_shutdown(javan_socket_handle fd, int input) {
        #if defined(_WIN32)
            return shutdown((SOCKET) fd, input != 0 ? SD_RECEIVE : SD_SEND);
        #else
            return shutdown((int) fd, input != 0 ? SHUT_RD : SHUT_WR);
        #endif
        }

        static int javan_socket_native_select(
            javan_socket_handle fd,
            fd_set* read_set,
            fd_set* write_set,
            struct timeval* timeout
        ) {
        #if defined(_WIN32)
            (void) fd;
            return select(0, read_set, write_set, NULL, timeout);
        #else
            return select((int) fd + 1, read_set, write_set, NULL, timeout);
        #endif
        }

        static int javan_socket_last_error_is_would_block(void) {
        #if defined(_WIN32)
            const int error = WSAGetLastError();
            return error == WSAEWOULDBLOCK || error == WSAETIMEDOUT;
        #else
            return errno == EAGAIN || errno == EWOULDBLOCK;
        #endif
        }

        static int javan_socket_last_error_is_interrupted(void) {
        #if defined(_WIN32)
            return WSAGetLastError() == WSAEINTR;
        #else
            return errno == EINTR;
        #endif
        }

        static int javan_socket_last_error_is_connecting(void) {
        #if defined(_WIN32)
            const int error = WSAGetLastError();
            return error == WSAEINPROGRESS || error == WSAEWOULDBLOCK;
        #else
            return errno == EINPROGRESS;
        #endif
        }

        static int javan_socket_native_begin_nonblocking(javan_socket_handle fd, int* restore_state) {
        #if defined(_WIN32)
            u_long enabled = 1UL;
            *restore_state = 0;
            return ioctlsocket((SOCKET) fd, FIONBIO, &enabled);
        #else
            const int flags = fcntl((int) fd, F_GETFL, 0);
            if (flags < 0) {
                return -1;
            }
            *restore_state = flags;
            return fcntl((int) fd, F_SETFL, flags | O_NONBLOCK);
        #endif
        }

        static int javan_socket_native_restore_blocking(javan_socket_handle fd, int restore_state) {
        #if defined(_WIN32)
            u_long disabled = 0UL;
            (void) restore_state;
            return ioctlsocket((SOCKET) fd, FIONBIO, &disabled);
        #else
            return fcntl((int) fd, F_SETFL, restore_state);
        #endif
        }

        static const char* javan_socket_inet_ntop(int family, const void* source, char* destination, unsigned long destination_size) {
        #if defined(_WIN32)
            return InetNtopA(family, (PVOID) source, destination, (DWORD) destination_size);
        #else
            return inet_ntop(family, source, destination, (socklen_t) destination_size);
        #endif
        }

        static int javan_socket_default_buffer_size(int option_name, const char* message) {
            const javan_socket_handle fd = javan_socket_native_open(AF_INET);
            if (javan_socket_handle_is_open(fd) == 0) {
                javan_panic(message);
            }
            const int value = javan_socket_getsockopt_buffer_size(fd, option_name, message);
            javan_socket_native_close(fd);
            return value <= 0 ? 8192 : value;
        }

        static int javan_server_socket_backlog_checked(int backlog) {
            if (backlog <= 0) {
                return 16;
            }
            return backlog;
        }

        static void javan_socket_host_checked(const char* host, struct sockaddr_storage* address, javan_socket_length* address_length, int port) {
            if (port < 0 || port > 65535) {
                javan_panic("socket port out of range");
            }
            memset(address, 0, sizeof(*address));
            if (host == NULL || strcmp(host, "localhost") == 0) {
                struct sockaddr_in* address4 = (struct sockaddr_in*) address;
                address4->sin_family = AF_INET;
                address4->sin_port = htons((unsigned short) port);
                address4->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
                *address_length = sizeof(*address4);
                return;
            }
            {
                struct sockaddr_in* address4 = (struct sockaddr_in*) address;
                address4->sin_family = AF_INET;
                address4->sin_port = htons((unsigned short) port);
                if (inet_pton(AF_INET, host, &address4->sin_addr) == 1) {
                    *address_length = sizeof(*address4);
                    return;
                }
            }
            {
                struct sockaddr_in6* address6 = (struct sockaddr_in6*) address;
                address6->sin6_family = AF_INET6;
                address6->sin6_port = htons((unsigned short) port);
                if (inet_pton(AF_INET6, host, &address6->sin6_addr) == 1) {
                    *address_length = sizeof(*address6);
                    return;
                }
            }
            javan_panic("unsupported socket host");
        }

        static void javan_socket_local_bind_checked(
            int remote_family,
            const char* host,
            struct sockaddr_storage* address,
            javan_socket_length* address_length,
            int port
        ) {
            if (port < 0 || port > 65535) {
                javan_panic("socket local port out of range");
            }
            memset(address, 0, sizeof(*address));
            if (host == NULL) {
                if (remote_family == AF_INET6) {
                    struct sockaddr_in6* address6 = (struct sockaddr_in6*) address;
                    address6->sin6_family = AF_INET6;
                    address6->sin6_port = htons((unsigned short) port);
                    address6->sin6_addr = in6addr_any;
                    *address_length = sizeof(*address6);
                    return;
                }
                struct sockaddr_in* address4 = (struct sockaddr_in*) address;
                address4->sin_family = AF_INET;
                address4->sin_port = htons((unsigned short) port);
                address4->sin_addr.s_addr = htonl(INADDR_ANY);
                *address_length = sizeof(*address4);
                return;
            }
            javan_socket_host_checked(host, address, address_length, port);
            if (((const struct sockaddr*) address)->sa_family != remote_family) {
                javan_panic("socket local host family mismatch");
            }
        }

        static void* javan_inet_address_from_sockaddr(const struct sockaddr* address) {
            if (address->sa_family == AF_INET) {
                const struct sockaddr_in* address4 = (const struct sockaddr_in*) address;
                char host[INET_ADDRSTRLEN];
                if (javan_socket_inet_ntop(AF_INET, (const void*) &address4->sin_addr, host, sizeof(host)) == NULL) {
                    javan_panic("socket address conversion failed");
                }
                const char* name = strcmp(host, "127.0.0.1") == 0 ? "localhost" : host;
                return javan_inet_address_new(host, name, name);
            }
            if (address->sa_family == AF_INET6) {
                const struct sockaddr_in6* address6 = (const struct sockaddr_in6*) address;
                char host[64];
                javan_inet_address_format_ipv6((const unsigned char*) &address6->sin6_addr, host, sizeof(host));
                const char* name = javan_inet_address_is_ipv6_loopback((const unsigned char*) &address6->sin6_addr) != 0 ? "localhost" : host;
                return javan_inet_address_new(host, name, name);
            }
            javan_panic("unsupported socket address family");
            return NULL;
        }

        static int javan_socket_port_from_sockaddr(const struct sockaddr* address) {
            if (address->sa_family == AF_INET) {
                return (int) ntohs(((const struct sockaddr_in*) address)->sin_port);
            }
            if (address->sa_family == AF_INET6) {
                return (int) ntohs(((const struct sockaddr_in6*) address)->sin6_port);
            }
            javan_panic("unsupported socket address family");
            return 0;
        }

        static int javan_socket_getsockopt_flag(javan_socket_handle fd, int level, int option_name, const char* message) {
            int value = 0;
            javan_socket_length length = (javan_socket_length) sizeof(value);
            if (javan_socket_native_get_option(fd, level, option_name, (void*) &value, &length) != 0) {
                javan_panic(message);
            }
            return value == 0 ? 0 : 1;
        }

        static void javan_socket_setsockopt_flag(javan_socket_handle fd, int level, int option_name, int enabled, const char* message) {
            int value = enabled == 0 ? 0 : 1;
            if (javan_socket_native_set_option(fd, level, option_name, (const void*) &value, (javan_socket_length) sizeof(value)) != 0) {
                javan_panic(message);
            }
        }

        static int javan_socket_getsockopt_int(javan_socket_handle fd, int level, int option_name, const char* message) {
            int value = 0;
            javan_socket_length length = (javan_socket_length) sizeof(value);
            if (javan_socket_native_get_option(fd, level, option_name, (void*) &value, &length) != 0) {
                javan_panic(message);
            }
            if (value < 0) {
                javan_panic(message);
            }
            return value;
        }

        static int javan_socket_normalize_buffer_size(int option_name, int value) {
        #if defined(__linux__)
            if ((option_name == SO_RCVBUF || option_name == SO_SNDBUF) && value > 1) {
                return value / 2;
            }
        #else
            (void) option_name;
        #endif
            return value;
        }

        static int javan_socket_getsockopt_buffer_size(javan_socket_handle fd, int option_name, const char* message) {
            return javan_socket_normalize_buffer_size(option_name, javan_socket_getsockopt_int(fd, SOL_SOCKET, option_name, message));
        }

        static int javan_socket_buffer_size_checked(int size) {
            if (size <= 0) {
                javan_panic("non-positive socket buffer size");
            }
            return size;
        }

        static void javan_socket_setsockopt_int(javan_socket_handle fd, int level, int option_name, int value, const char* message) {
            if (javan_socket_native_set_option(fd, level, option_name, (const void*) &value, (javan_socket_length) sizeof(value)) != 0) {
                javan_panic(message);
            }
        }

        static int javan_socket_timeout_checked(int timeout_millis) {
            if (timeout_millis < 0) {
                javan_panic("negative socket timeout");
            }
            return timeout_millis;
        }

        static int javan_socket_linger_checked(int linger_seconds) {
            if (linger_seconds < 0) {
                javan_panic("negative socket linger");
            }
            if (linger_seconds > 65535) {
                return 65535;
            }
            return linger_seconds;
        }

        static int javan_socket_getsockopt_linger(javan_socket_handle fd, const char* message) {
            struct linger value;
            long long linger;
            javan_socket_length length = (javan_socket_length) sizeof(value);
            memset(&value, 0, sizeof(value));
            if (javan_socket_native_get_option(fd, SOL_SOCKET, SO_LINGER, (void*) &value, &length) != 0) {
                javan_panic(message);
            }
            if (value.l_onoff == 0) {
                return -1;
            }
            linger = (long long) value.l_linger;
            if (linger < 0) {
                javan_panic(message);
            }
            return linger > 65535 ? 65535 : (int) linger;
        }

        static void javan_socket_setsockopt_linger(javan_socket_handle fd, int enabled, int linger_seconds, const char* message) {
            struct linger value;
            value.l_onoff = enabled == 0 ? 0 : 1;
            value.l_linger = enabled == 0 ? 0 : linger_seconds;
            if (javan_socket_native_set_option(fd, SOL_SOCKET, SO_LINGER, (const void*) &value, (javan_socket_length) sizeof(value)) != 0) {
                javan_panic(message);
            }
        }

        static int javan_socket_traffic_class_level(javan_socket_handle fd, int* option_name_out) {
            struct sockaddr_storage local_address;
            javan_socket_length local_length = (javan_socket_length) sizeof(local_address);
            if (javan_socket_native_get_name(fd, 0, (struct sockaddr*) &local_address, &local_length) != 0) {
                javan_panic("socket local address lookup failed");
            }
            if (((struct sockaddr*) &local_address)->sa_family == AF_INET) {
                *option_name_out = IP_TOS;
                return IPPROTO_IP;
            }
            if (((struct sockaddr*) &local_address)->sa_family == AF_INET6) {
            #if defined(IPV6_TCLASS)
                *option_name_out = IPV6_TCLASS;
                return IPPROTO_IPV6;
            #else
                javan_panic("socket traffic class is unsupported for IPv6 on this host");
            #endif
            }
            javan_panic("unsupported socket address family");
            return 0;
        }

        static int javan_socket_traffic_class_checked(int traffic_class) {
            if (traffic_class < 0 || traffic_class > 255) {
                javan_panic("socket traffic class out of range");
            }
            return traffic_class;
        }

        static int javan_socket_getsockopt_traffic_class(javan_socket_handle fd, const char* message) {
            int option_name = 0;
            int level = javan_socket_traffic_class_level(fd, &option_name);
            int value = javan_socket_getsockopt_int(fd, level, option_name, message);
        #if defined(__linux__)
            if (option_name == IP_TOS) {
                return value & 0xFC;
            }
        #endif
            return value;
        }

        static void javan_socket_setsockopt_traffic_class(javan_socket_handle fd, int traffic_class, const char* message) {
            int option_name = 0;
            int level = javan_socket_traffic_class_level(fd, &option_name);
            javan_socket_setsockopt_int(fd, level, option_name, traffic_class, message);
        }

        static void javan_socket_apply_receive_timeout(javan_socket_handle fd, int timeout_millis, const char* message) {
        #if defined(_WIN32)
            const int timeout = timeout_millis;
            if (javan_socket_native_set_option(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, (javan_socket_length) sizeof(timeout)) != 0) {
                javan_panic(message);
            }
        #else
            struct timeval timeout;
            timeout.tv_sec = (time_t) (timeout_millis / 1000);
            timeout.tv_usec = (long) ((timeout_millis % 1000) * 1000);
            if (javan_socket_native_set_option(fd, SOL_SOCKET, SO_RCVTIMEO, (const void*) &timeout, (javan_socket_length) sizeof(timeout)) != 0) {
                javan_panic(message);
            }
        #endif
        }

        static void javan_socket_wait_readable(javan_socket_handle fd, int timeout_millis, const char* timeout_message, const char* wait_message) {
            if (timeout_millis <= 0) {
                return;
            }
            fd_set read_set;
            FD_ZERO(&read_set);
            FD_SET(fd, &read_set);
            struct timeval timeout;
            timeout.tv_sec = (time_t) (timeout_millis / 1000);
            timeout.tv_usec = (long) ((timeout_millis % 1000) * 1000);
            int ready = javan_socket_native_select(fd, &read_set, NULL, &timeout);
            if (ready == 0) {
                javan_panic(timeout_message);
            }
            if (ready < 0) {
                javan_panic(wait_message);
            }
        }

        static void javan_socket_populate_names(javan_socket_handle fd, void** local_address_out, int* local_port_out, void** remote_address_out, int* remote_port_out) {
            struct sockaddr_storage local_address;
            javan_socket_length local_length = (javan_socket_length) sizeof(local_address);
            if (javan_socket_native_get_name(fd, 0, (struct sockaddr*) &local_address, &local_length) != 0) {
                javan_panic("socket local address lookup failed");
            }
            struct sockaddr_storage remote_address;
            javan_socket_length remote_length = (javan_socket_length) sizeof(remote_address);
            if (javan_socket_native_get_name(fd, 1, (struct sockaddr*) &remote_address, &remote_length) != 0) {
                javan_panic("socket remote address lookup failed");
            }
            *local_address_out = javan_inet_address_from_sockaddr((const struct sockaddr*) &local_address);
            *remote_address_out = javan_inet_address_from_sockaddr((const struct sockaddr*) &remote_address);
            *local_port_out = javan_socket_port_from_sockaddr((const struct sockaddr*) &local_address);
            *remote_port_out = javan_socket_port_from_sockaddr((const struct sockaddr*) &remote_address);
        }

        static void javan_socket_populate_options(javan_socket_handle fd, int* tcp_no_delay_out, int* keep_alive_out, int* reuse_address_out, int* oob_inline_out, int* traffic_class_out) {
            *tcp_no_delay_out = javan_socket_getsockopt_flag(fd, IPPROTO_TCP, TCP_NODELAY, "socket TCP_NODELAY lookup failed");
            *keep_alive_out = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_KEEPALIVE, "socket SO_KEEPALIVE lookup failed");
            *reuse_address_out = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_REUSEADDR, "socket SO_REUSEADDR lookup failed");
            *oob_inline_out = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_OOBINLINE, "socket SO_OOBINLINE lookup failed");
            *traffic_class_out = javan_socket_getsockopt_traffic_class(fd, "socket traffic class lookup failed");
        }

        static void javan_socket_initialize(javan_socket* socket, void* local_address) {
            socket->magic = JAVAN_SOCKET_MAGIC;
            socket->fd = JAVAN_SOCKET_INVALID;
            socket->connected = 0;
            socket->closed = 0;
            socket->bound = 0;
            socket->input_shutdown = 0;
            socket->output_shutdown = 0;
            socket->so_linger = -1;
            socket->oob_inline = 0;
            socket->traffic_class = 0;
            socket->local_port = -1;
            socket->remote_port = 0;
            socket->so_timeout = 0;
            socket->tcp_no_delay = 0;
            socket->keep_alive = 0;
            socket->reuse_address = 0;
            socket->receive_buffer_size = javan_socket_default_buffer_size(SO_RCVBUF, "socket default receive buffer lookup failed");
            socket->send_buffer_size = javan_socket_default_buffer_size(SO_SNDBUF, "socket default send buffer lookup failed");
            socket->local_address = (javan_inet_address*) local_address;
            socket->remote_address = NULL;
        }

        void* javan_socket_new(void) {
            javan_socket_runtime_initialize();
            void* local_address = javan_inet_address_new(NULL, NULL, NULL);
            void* local_address_root = local_address;
            void* socket_root = NULL;
            void** roots[] = {
                (void**) &local_address_root,
                (void**) &socket_root
            };
            javan_root_frame_push(roots, 2);
            javan_socket* socket = (javan_socket*) javan_alloc_rooted(sizeof(javan_socket), &socket_root);
            javan_socket_initialize(socket, local_address_root);
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SOCKET);
            javan_root_frame_pop(roots);
            return socket_root;
        }

        static void javan_socket_connect_native_timeout(
            javan_socket_handle fd,
            const struct sockaddr* address,
            javan_socket_length address_length,
            int timeout_millis
        ) {
            const int timeout = javan_socket_timeout_checked(timeout_millis);
            if (timeout == 0) {
                if (javan_socket_native_connect(fd, address, address_length) != 0) {
                    javan_panic("socket connect failed");
                }
                return;
            }
            int restore_state = 0;
            if (javan_socket_native_begin_nonblocking(fd, &restore_state) != 0) {
                javan_panic("socket connect nonblocking setup failed");
            }
            const int result = javan_socket_native_connect(fd, address, address_length);
            if (result != 0 && javan_socket_last_error_is_connecting() == 0) {
                javan_socket_native_restore_blocking(fd, restore_state);
                javan_panic("socket connect failed");
            }
            if (result != 0) {
                fd_set write_set;
                FD_ZERO(&write_set);
                FD_SET(fd, &write_set);
                struct timeval timeout_value;
                timeout_value.tv_sec = (time_t) (timeout / 1000);
                timeout_value.tv_usec = (long) ((timeout % 1000) * 1000);
                const int ready = javan_socket_native_select(fd, NULL, &write_set, &timeout_value);
                if (ready == 0) {
                    javan_socket_native_restore_blocking(fd, restore_state);
                    javan_panic("socket connect timed out");
                }
                if (ready < 0) {
                    javan_socket_native_restore_blocking(fd, restore_state);
                    javan_panic("socket connect wait failed");
                }
                int error = 0;
                javan_socket_length error_length = (javan_socket_length) sizeof(error);
                if (javan_socket_native_get_option(fd, SOL_SOCKET, SO_ERROR, (void*) &error, &error_length) != 0) {
                    javan_socket_native_restore_blocking(fd, restore_state);
                    javan_panic("socket connect state lookup failed");
                }
                if (error != 0) {
                    javan_socket_native_restore_blocking(fd, restore_state);
                    javan_panic("socket connect failed");
                }
            }
            if (javan_socket_native_restore_blocking(fd, restore_state) != 0) {
                javan_panic("socket connect blocking restore failed");
            }
        }

        static void javan_socket_assign_connected_fd(void* socket_value, javan_socket_handle fd) {
            void* local_address = NULL;
            void* remote_address = NULL;
            int local_port = 0;
            int remote_port = 0;
            int tcp_no_delay = 0;
            int keep_alive = 0;
            int reuse_address = 0;
            int oob_inline = 0;
            int traffic_class = 0;
            int receive_buffer_size = 0;
            int send_buffer_size = 0;
            void* socket_root = socket_value;
            void** javan_socket_assign_roots[] = {
                (void**) &socket_root,
                (void**) &local_address,
                (void**) &remote_address
            };
            javan_root_frame_push(javan_socket_assign_roots, 3);
            javan_socket_populate_names(fd, &local_address, &local_port, &remote_address, &remote_port);
            javan_socket_populate_options(fd, &tcp_no_delay, &keep_alive, &reuse_address, &oob_inline, &traffic_class);
            receive_buffer_size = javan_socket_getsockopt_buffer_size(fd, SO_RCVBUF, "socket SO_RCVBUF lookup failed");
            send_buffer_size = javan_socket_getsockopt_buffer_size(fd, SO_SNDBUF, "socket SO_SNDBUF lookup failed");
            javan_runtime_lock_enter();
            javan_socket* socket = javan_socket_checked(socket_root);
            socket->fd = fd;
            socket->connected = 1;
            socket->closed = 0;
            socket->bound = 1;
            socket->input_shutdown = 0;
            socket->output_shutdown = 0;
            socket->so_linger = -1;
            socket->oob_inline = oob_inline;
            socket->traffic_class = traffic_class;
            socket->local_port = local_port;
            socket->remote_port = remote_port;
            socket->tcp_no_delay = tcp_no_delay;
            socket->keep_alive = keep_alive;
            socket->reuse_address = reuse_address;
            socket->receive_buffer_size = receive_buffer_size <= 0 ? socket->receive_buffer_size : receive_buffer_size;
            socket->send_buffer_size = send_buffer_size <= 0 ? socket->send_buffer_size : send_buffer_size;
            socket->local_address = (javan_inet_address*) local_address;
            socket->remote_address = (javan_inet_address*) remote_address;
            javan_runtime_lock_leave();
            javan_root_frame_pop(javan_socket_assign_roots);
        }

        static void javan_socket_wrap_connected_fd_into(void** result, javan_socket_handle fd) {
            if (result == NULL) {
                javan_panic("missing connected socket result");
            }
            *result = NULL;
            void** roots[] = { result };
            javan_root_frame_push(roots, 1);
            javan_socket_runtime_initialize();
            javan_socket* socket = (javan_socket*) javan_alloc_rooted(sizeof(javan_socket), result);
            javan_socket_initialize(socket, NULL);
            javan_socket_assign_connected_fd(*result, fd);
            javan_update_runtime_allocation_kind(*result, JAVAN_RUNTIME_KIND_SOCKET);
            javan_root_frame_pop(roots);
        }

        static void* javan_socket_wrap_connected_fd(javan_socket_handle fd) {
            void* result = NULL;
            void** roots[] = { &result };
            javan_root_frame_push(roots, 1);
            javan_socket_wrap_connected_fd_into(&result, fd);
            javan_root_frame_pop(roots);
            return result;
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
            if (socket->closed != 0 || javan_socket_handle_is_open(socket->fd) == 0) {
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
            (void) javan_socket_checked(socket_value);
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
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_storage address;
            javan_socket_length address_length = 0;
            javan_socket_host_checked(host, &address, &address_length, port);
            javan_socket_handle fd = javan_socket_native_open(((struct sockaddr*) &address)->sa_family);
            if (javan_socket_handle_is_open(fd) == 0) {
                javan_panic("socket open failed");
            }
            javan_socket_connect_native_timeout(fd, (struct sockaddr*) &address, address_length, 0);
            return javan_socket_wrap_connected_fd(fd);
        }

        static void* javan_socket_connect_host_timeout(void* host_value, int port, int timeout_millis) {
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_storage address;
            javan_socket_length address_length = 0;
            javan_socket_host_checked(host, &address, &address_length, port);
            javan_socket_handle fd = javan_socket_native_open(((struct sockaddr*) &address)->sa_family);
            if (javan_socket_handle_is_open(fd) == 0) {
                javan_panic("socket open failed");
            }
            javan_socket_connect_native_timeout(fd, (struct sockaddr*) &address, address_length, timeout_millis);
            return javan_socket_wrap_connected_fd(fd);
        }

        void* javan_socket_connect_host_config(void* host_value, int port, void* local_address_value, int local_port) {
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            const char* local_host = local_address_value == NULL
                ? NULL
                : (const char*) javan_inet_address_checked(local_address_value)->host_address;
            struct sockaddr_storage remote_address;
            javan_socket_length remote_length = 0;
            javan_socket_host_checked(host, &remote_address, &remote_length, port);
            javan_socket_handle fd = javan_socket_native_open(((struct sockaddr*) &remote_address)->sa_family);
            if (javan_socket_handle_is_open(fd) == 0) {
                javan_panic("socket open failed");
            }
            if (local_host != NULL || local_port != 0) {
                struct sockaddr_storage local_address;
                javan_socket_length local_length = 0;
                javan_socket_local_bind_checked(
                    ((struct sockaddr*) &remote_address)->sa_family,
                    local_host,
                    &local_address,
                    &local_length,
                    local_port
                );
                if (javan_socket_native_bind(fd, (struct sockaddr*) &local_address, local_length) != 0) {
                    javan_socket_native_close(fd);
                    javan_panic("socket local bind failed");
                }
            }
            javan_socket_connect_native_timeout(fd, (struct sockaddr*) &remote_address, remote_length, 0);
            return javan_socket_wrap_connected_fd(fd);
        }

        void* javan_socket_connect_address_config(void* remote_address_value, int port, void* local_address_value, int local_port) {
            if (remote_address_value == NULL) {
                javan_panic("null inet address");
            }
            void* remote_host = javan_inet_address_checked(remote_address_value)->host_address;
            return javan_socket_connect_host_config(remote_host, port, local_address_value, local_port);
        }

        void javan_socket_connect_socket_address(void* value, void* address) {
            javan_socket_connect_socket_address_timeout(value, address, 0);
        }

        void javan_socket_connect_socket_address_timeout(void* value, void* address, int timeout_millis) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->connected != 0) {
                javan_panic("socket is already connected");
            }
            javan_inet_socket_address* remote = javan_inet_socket_address_checked(address);
            void* connected = javan_socket_connect_host_timeout((void*) remote->address->host_address, remote->port, timeout_millis);
            void* socket_root = value;
            void* connected_root = connected;
            void** javan_socket_connect_roots[] = {
                (void**) &socket_root,
                (void**) &connected_root
            };
            javan_root_frame_push(javan_socket_connect_roots, 2);
            javan_socket* target = javan_socket_checked(socket_root);
            javan_socket* source = javan_socket_checked(connected_root);
            javan_socket_set_so_timeout(connected_root, target->so_timeout);
            javan_socket_set_so_linger(connected_root, target->so_linger >= 0 ? 1 : 0, target->so_linger >= 0 ? target->so_linger : 0);
            javan_socket_set_oob_inline(connected_root, target->oob_inline);
            javan_socket_set_traffic_class(connected_root, target->traffic_class);
            javan_socket_set_tcp_no_delay(connected_root, target->tcp_no_delay);
            javan_socket_set_keep_alive(connected_root, target->keep_alive);
            javan_socket_set_reuse_address(connected_root, target->reuse_address);
            javan_socket_set_receive_buffer_size(connected_root, target->receive_buffer_size);
            javan_socket_set_send_buffer_size(connected_root, target->send_buffer_size);
            source = javan_socket_checked(connected_root);
            *target = *source;
            javan_root_frame_pop(javan_socket_connect_roots);
        }

        int javan_socket_is_connected(void* value) {
            return javan_socket_checked(value)->connected != 0;
        }

        int javan_socket_is_closed(void* value) {
            return javan_socket_checked(value)->closed != 0;
        }

        int javan_socket_is_bound(void* value) {
            return javan_socket_checked(value)->bound != 0;
        }

        int javan_socket_is_input_shutdown(void* value) {
            return javan_socket_checked(value)->input_shutdown != 0;
        }

        int javan_socket_is_output_shutdown(void* value) {
            return javan_socket_checked(value)->output_shutdown != 0;
        }

        int javan_socket_get_port(void* value) {
            return javan_socket_checked(value)->remote_port;
        }

        int javan_socket_get_local_port(void* value) {
            return javan_socket_checked(value)->local_port;
        }

        int javan_socket_get_so_timeout(void* value) {
            return javan_socket_checked(value)->so_timeout;
        }

        void javan_socket_set_so_timeout(void* value, int timeout_millis) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int timeout = javan_socket_timeout_checked(timeout_millis);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_apply_receive_timeout(socket->fd, timeout, "socket SO_RCVTIMEO update failed");
            }
            socket->so_timeout = timeout;
        }

        int javan_socket_get_so_linger(void* value) {
            return javan_socket_checked(value)->so_linger;
        }

        void javan_socket_set_so_linger(void* value, int enabled, int linger_seconds) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (enabled == 0) {
                if (javan_socket_handle_is_open(socket->fd) != 0) {
                    javan_socket_setsockopt_linger(socket->fd, 0, 0, "socket SO_LINGER update failed");
                }
                socket->so_linger = -1;
                return;
            }
            int checked = javan_socket_linger_checked(linger_seconds);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_linger(socket->fd, 1, checked, "socket SO_LINGER update failed");
            }
            socket->so_linger = checked;
        }

        int javan_socket_get_oob_inline(void* value) {
            return javan_socket_checked(value)->oob_inline;
        }

        void javan_socket_set_oob_inline(void* value, int enabled) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_OOBINLINE, enabled, "socket SO_OOBINLINE update failed");
            }
            socket->oob_inline = enabled == 0 ? 0 : 1;
        }

        int javan_socket_get_traffic_class(void* value) {
            return javan_socket_checked(value)->traffic_class;
        }

        void javan_socket_set_traffic_class(void* value, int traffic_class) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int checked = javan_socket_traffic_class_checked(traffic_class);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_traffic_class(socket->fd, checked, "socket traffic class update failed");
                socket->traffic_class = javan_socket_getsockopt_traffic_class(socket->fd, "socket traffic class lookup failed");
                return;
            }
            socket->traffic_class = checked;
        }

        int javan_socket_get_tcp_no_delay(void* value) {
            return javan_socket_checked(value)->tcp_no_delay;
        }

        void javan_socket_set_tcp_no_delay(void* value, int enabled) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_flag(socket->fd, IPPROTO_TCP, TCP_NODELAY, enabled, "socket TCP_NODELAY update failed");
            }
            socket->tcp_no_delay = enabled == 0 ? 0 : 1;
        }

        int javan_socket_get_keep_alive(void* value) {
            return javan_socket_checked(value)->keep_alive;
        }

        void javan_socket_set_keep_alive(void* value, int enabled) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_KEEPALIVE, enabled, "socket SO_KEEPALIVE update failed");
            }
            socket->keep_alive = enabled == 0 ? 0 : 1;
        }

        int javan_socket_get_reuse_address(void* value) {
            return javan_socket_checked(value)->reuse_address;
        }

        void javan_socket_set_reuse_address(void* value, int enabled) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_REUSEADDR, enabled, "socket SO_REUSEADDR update failed");
            }
            socket->reuse_address = enabled == 0 ? 0 : 1;
        }

        int javan_socket_get_receive_buffer_size(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) == 0) {
                return socket->receive_buffer_size;
            }
            return javan_socket_getsockopt_buffer_size(socket->fd, SO_RCVBUF, "socket SO_RCVBUF lookup failed");
        }

        void javan_socket_set_receive_buffer_size(void* value, int size) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int checked = javan_socket_buffer_size_checked(size);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_int(socket->fd, SOL_SOCKET, SO_RCVBUF, checked, "socket SO_RCVBUF update failed");
            }
            socket->receive_buffer_size = checked;
        }

        int javan_socket_get_send_buffer_size(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) == 0) {
                return socket->send_buffer_size;
            }
            return javan_socket_getsockopt_buffer_size(socket->fd, SO_SNDBUF, "socket SO_SNDBUF lookup failed");
        }

        void javan_socket_set_send_buffer_size(void* value, int size) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int checked = javan_socket_buffer_size_checked(size);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_int(socket->fd, SOL_SOCKET, SO_SNDBUF, checked, "socket SO_SNDBUF update failed");
            }
            socket->send_buffer_size = checked;
        }

        void* javan_socket_get_local_address(void* value) {
            return javan_socket_checked(value)->local_address;
        }

        void* javan_socket_get_inet_address(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            return socket->connected == 0 ? NULL : socket->remote_address;
        }

        void* javan_socket_get_local_socket_address(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->bound == 0 || socket->local_port < 0) {
                return NULL;
            }
            return javan_inet_socket_address_from_address((void*) socket->local_address, socket->local_port);
        }

        void* javan_socket_get_remote_socket_address(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (socket->connected == 0) {
                return NULL;
            }
            return javan_inet_socket_address_from_address((void*) socket->remote_address, socket->remote_port);
        }

        void* javan_socket_get_channel(void* value) {
            javan_socket_checked(value);
            return NULL;
        }

        void* javan_socket_input_stream(void* value) {
            return javan_socket_stream_new(value, 0);
        }

        void* javan_socket_output_stream(void* value) {
            return javan_socket_stream_new(value, 1);
        }

        int javan_socket_input_stream_read(void* value) {
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            if (socket->input_shutdown != 0) {
                javan_panic("socket input is shutdown");
            }
            javan_socket_wait_readable(socket->fd, socket->so_timeout, "socket read timed out", "socket read wait failed");
            unsigned char byte = 0;
            int result = javan_socket_native_receive(socket->fd, &byte, 1);
            if (result < 0) {
                if (javan_socket_last_error_is_would_block() != 0) {
                    javan_panic("socket read timed out");
                }
                javan_panic("socket read failed");
            }
            if (result == 0) {
                return -1;
            }
            return byte;
        }

        int javan_socket_input_stream_read_bytes(void* value, void* bytes_value) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            return javan_socket_input_stream_read_bytes_range(value, bytes_value, 0, bytes->length);
        }

        int javan_socket_input_stream_read_bytes_range(void* value, void* bytes_value, int offset, int length) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_stream_range_checked(bytes, offset, length);
            if (length == 0) {
                return 0;
            }
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            if (socket->input_shutdown != 0) {
                javan_panic("socket input is shutdown");
            }
            javan_socket_wait_readable(socket->fd, socket->so_timeout, "socket read timed out", "socket read wait failed");
            int result = javan_socket_native_receive(socket->fd, bytes->values + offset, length);
            if (result < 0) {
                if (javan_socket_last_error_is_would_block() != 0) {
                    javan_panic("socket read timed out");
                }
                javan_panic("socket read failed");
            }
            if (result == 0) {
                return -1;
            }
            return result;
        }

        void javan_socket_shutdown_input(void* value) {
            javan_socket* socket = javan_socket_open_checked(value);
            if (socket->input_shutdown != 0) {
                javan_panic("socket input is already shutdown");
            }
            if (javan_socket_native_shutdown(socket->fd, 1) != 0) {
                javan_panic("socket shutdown input failed");
            }
            socket->input_shutdown = 1;
        }

        void javan_socket_input_stream_close(void* value) {
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket_close((void*) stream->socket);
        }

        void javan_socket_output_stream_write(void* value, int byte_value) {
            javan_socket_output_stream_value* stream = javan_socket_output_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            if (socket->output_shutdown != 0) {
                javan_panic("socket output is shutdown");
            }
            unsigned char byte = (unsigned char) (byte_value & 0xff);
            int written = javan_socket_native_send(socket->fd, &byte, 1, 0);
            if (written != 1) {
                javan_panic("socket write failed");
            }
        }

        void javan_socket_output_stream_write_bytes(void* value, void* bytes_value) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_output_stream_write_bytes_range(value, bytes_value, 0, bytes->length);
        }

        void javan_socket_output_stream_write_bytes_range(void* value, void* bytes_value, int offset, int length) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_stream_range_checked(bytes, offset, length);
            if (length == 0) {
                return;
            }
            javan_socket_output_stream_value* stream = javan_socket_output_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            if (socket->output_shutdown != 0) {
                javan_panic("socket output is shutdown");
            }
            int written = 0;
            while (written < length) {
                int chunk = javan_socket_native_send(socket->fd, bytes->values + offset + written, length - written, 0);
                if (chunk <= 0) {
                    javan_panic("socket write failed");
                }
                written += chunk;
            }
        }

        void javan_socket_output_stream_flush(void* value) {
            (void) javan_socket_output_stream_checked(value);
        }

        void javan_socket_shutdown_output(void* value) {
            javan_socket* socket = javan_socket_open_checked(value);
            if (socket->output_shutdown != 0) {
                javan_panic("socket output is already shutdown");
            }
            if (javan_socket_native_shutdown(socket->fd, 0) != 0) {
                javan_panic("socket shutdown output failed");
            }
            socket->output_shutdown = 1;
        }

        void javan_socket_output_stream_close(void* value) {
            javan_socket_output_stream_value* stream = javan_socket_output_stream_checked(value);
            javan_socket_close((void*) stream->socket);
        }

        void javan_socket_close(void* value) {
            javan_socket* socket = javan_socket_checked(value);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_native_close(socket->fd);
                socket->fd = JAVAN_SOCKET_INVALID;
            }
            if (socket->bound != 0) {
                socket->input_shutdown = 1;
                socket->output_shutdown = 1;
            }
            socket->closed = 1;
        }

        void* javan_server_socket_new(void) {
            javan_socket_runtime_initialize();
            javan_server_socket* socket = (javan_server_socket*) javan_alloc(sizeof(javan_server_socket));
            socket->magic = JAVAN_SERVER_SOCKET_MAGIC;
            socket->fd = JAVAN_SOCKET_INVALID;
            socket->bound = 0;
            socket->closed = 0;
            socket->local_port = -1;
            socket->so_timeout = 0;
            socket->reuse_address = 1;
            socket->receive_buffer_size = javan_socket_default_buffer_size(SO_RCVBUF, "server socket default receive buffer lookup failed");
            socket->local_address = NULL;
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SERVER_SOCKET);
            return socket;
        }

        void* javan_server_socket_bind(int port) {
            return javan_server_socket_bind_config(NULL, port, 16);
        }

        void* javan_server_socket_bind_config(void* host_value, int port, int backlog) {
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_storage address;
            javan_socket_length address_length = 0;
            javan_socket_host_checked(host, &address, &address_length, port);
            javan_socket_handle fd = javan_socket_native_open(((struct sockaddr*) &address)->sa_family);
            if (javan_socket_handle_is_open(fd) == 0) {
                javan_panic("server socket open failed");
            }
            javan_socket_setsockopt_flag(fd, SOL_SOCKET, SO_REUSEADDR, 1, "server socket SO_REUSEADDR update failed");
            if (javan_socket_native_bind(fd, (struct sockaddr*) &address, address_length) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket bind failed");
            }
            if (javan_socket_native_listen(fd, javan_server_socket_backlog_checked(backlog)) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket listen failed");
            }
            struct sockaddr_storage bound;
            javan_socket_length bound_length = (javan_socket_length) sizeof(bound);
            if (javan_socket_native_get_name(fd, 0, (struct sockaddr*) &bound, &bound_length) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket local address lookup failed");
            }
            void* local_address = NULL;
            void** javan_server_socket_roots[] = {
                (void**) &local_address
            };
            javan_root_frame_push(javan_server_socket_roots, 1);
            local_address = javan_inet_address_from_sockaddr((const struct sockaddr*) &bound);
            javan_server_socket* socket = (javan_server_socket*) javan_alloc(sizeof(javan_server_socket));
            void* socket_root = (void*) socket;
            void** javan_server_socket_owner_roots[] = {
                (void**) &local_address,
                (void**) &socket_root
            };
            javan_root_frame_push(javan_server_socket_owner_roots, 2);
            socket->magic = JAVAN_SERVER_SOCKET_MAGIC;
            socket->fd = fd;
            socket->bound = 1;
            socket->closed = 0;
            socket->local_port = javan_socket_port_from_sockaddr((const struct sockaddr*) &bound);
            socket->so_timeout = 0;
            socket->reuse_address = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_REUSEADDR, "server socket SO_REUSEADDR lookup failed");
            socket->receive_buffer_size = javan_socket_getsockopt_buffer_size(fd, SO_RCVBUF, "server socket SO_RCVBUF lookup failed");
            socket->local_address = (javan_inet_address*) local_address;
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SERVER_SOCKET);
            javan_root_frame_pop(javan_server_socket_owner_roots);
            javan_root_frame_pop(javan_server_socket_roots);
            return socket;
        }

        void javan_server_socket_bind_socket_address(void* value, void* address) {
            javan_server_socket_bind_socket_address_backlog(value, address, 16);
        }

        void javan_server_socket_bind_socket_address_backlog(void* value, void* address, int backlog) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (socket->bound != 0) {
                javan_panic("server socket is already bound");
            }
            javan_inet_socket_address* bind_address = javan_inet_socket_address_checked(address);
            void* bound = javan_server_socket_bind_config((void*) bind_address->address->host_address, bind_address->port, backlog);
            void* socket_root = value;
            void* bound_root = bound;
            void** javan_server_socket_bind_roots[] = {
                (void**) &socket_root,
                (void**) &bound_root
            };
            javan_root_frame_push(javan_server_socket_bind_roots, 2);
            javan_server_socket* target = javan_server_socket_checked(socket_root);
            javan_server_socket_set_reuse_address(bound_root, target->reuse_address);
            javan_server_socket_set_so_timeout(bound_root, target->so_timeout);
            javan_server_socket_set_receive_buffer_size(bound_root, target->receive_buffer_size);
            javan_server_socket* source = javan_server_socket_checked(bound_root);
            *target = *source;
            javan_root_frame_pop(javan_server_socket_bind_roots);
        }

        int javan_server_socket_get_local_port(void* value) {
            return javan_server_socket_checked(value)->local_port;
        }

        int javan_server_socket_is_bound(void* value) {
            return javan_server_socket_checked(value)->bound != 0;
        }

        int javan_server_socket_is_closed(void* value) {
            return javan_server_socket_checked(value)->closed != 0;
        }

        int javan_server_socket_get_so_timeout(void* value) {
            return javan_server_socket_checked(value)->so_timeout;
        }

        void javan_server_socket_set_so_timeout(void* value, int timeout_millis) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            int timeout = javan_socket_timeout_checked(timeout_millis);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_apply_receive_timeout(socket->fd, timeout, "server socket SO_RCVTIMEO update failed");
            }
            socket->so_timeout = timeout;
        }

        void* javan_server_socket_get_inet_address(void* value) {
            return javan_server_socket_checked(value)->local_address;
        }

        int javan_server_socket_get_reuse_address(void* value) {
            return javan_server_socket_checked(value)->reuse_address;
        }

        void javan_server_socket_set_reuse_address(void* value, int enabled) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_REUSEADDR, enabled, "server socket SO_REUSEADDR update failed");
            }
            socket->reuse_address = enabled == 0 ? 0 : 1;
        }

        int javan_server_socket_get_receive_buffer_size(void* value) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (javan_socket_handle_is_open(socket->fd) == 0) {
                return socket->receive_buffer_size;
            }
            return javan_socket_getsockopt_buffer_size(socket->fd, SO_RCVBUF, "server socket SO_RCVBUF lookup failed");
        }

        void javan_server_socket_set_receive_buffer_size(void* value, int size) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            int checked = javan_socket_buffer_size_checked(size);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_setsockopt_int(socket->fd, SOL_SOCKET, SO_RCVBUF, checked, "server socket SO_RCVBUF update failed");
            }
            socket->receive_buffer_size = checked;
        }

        void* javan_server_socket_get_local_socket_address(void* value) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->bound == 0 || socket->local_address == NULL || socket->local_port < 0) {
                return NULL;
            }
            return javan_inet_socket_address_from_address((void*) socket->local_address, socket->local_port);
        }

        void* javan_server_socket_get_channel(void* value) {
            javan_server_socket_checked(value);
            return NULL;
        }

        void* javan_server_socket_accept(void* value) {
            javan_server_socket* server = javan_server_socket_checked(value);
            if (server->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (javan_socket_handle_is_open(server->fd) == 0 || server->bound == 0) {
                javan_panic("server socket is not bound");
            }
            javan_socket_wait_readable(server->fd, server->so_timeout, "server socket accept timed out", "server socket accept wait failed");
            javan_socket_handle accepted = javan_socket_native_accept(server->fd);
            if (javan_socket_handle_is_open(accepted) == 0) {
                if (javan_socket_last_error_is_would_block() != 0) {
                    javan_panic("server socket accept timed out");
                }
                javan_panic("server socket accept failed");
            }
            return javan_socket_wrap_connected_fd(accepted);
        }

        void javan_server_socket_close(void* value) {
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (javan_socket_handle_is_open(socket->fd) != 0) {
                javan_socket_native_close(socket->fd);
                socket->fd = JAVAN_SOCKET_INVALID;
            }
            socket->closed = 1;
        }

        """;

    private static final String SOURCE_TAIL_C = """

        static javan_http_server_value* javan_http_server_checked(void* value) {
            if (value == NULL) {
                javan_panic("null HttpServer");
            }
            javan_http_server_value* server = (javan_http_server_value*) value;
            if (server->magic != JAVAN_HTTP_SERVER_MAGIC || server->socket == NULL) {
                javan_panic("invalid HttpServer state");
            }
            return server;
        }

        static javan_http_exchange_value* javan_http_exchange_checked(void* value) {
            if (value == NULL) {
                javan_panic("null HttpExchange");
            }
            javan_http_exchange_value* exchange = (javan_http_exchange_value*) value;
            if (exchange->magic != JAVAN_HTTP_EXCHANGE_MAGIC || exchange->socket == NULL) {
                javan_panic("invalid HttpExchange state");
            }
            return exchange;
        }

        static javan_http_exchange_output_stream_value* javan_http_exchange_output_stream_checked(void* value) {
            if (value == NULL) {
                javan_panic("null HttpExchange response body");
            }
            javan_http_exchange_output_stream_value* stream = (javan_http_exchange_output_stream_value*) value;
            if (stream->magic != JAVAN_HTTP_EXCHANGE_OUTPUT_STREAM_MAGIC || stream->exchange == NULL) {
                javan_panic("invalid HttpExchange response body");
            }
            return stream;
        }

        static int javan_http_server_stopped(javan_http_server_value* server) {
            javan_runtime_lock_enter();
            int stopped = server->stopped;
            javan_runtime_lock_leave();
            return stopped;
        }

        static void javan_http_server_write_all(javan_socket* socket, const char* bytes, int length) {
        if (length < 0) {
            javan_panic("invalid HTTP response length");
        }
        #if defined(SO_NOSIGPIPE)
        int no_sigpipe = 1;
        if (javan_socket_native_set_option(socket->fd, SOL_SOCKET, SO_NOSIGPIPE, &no_sigpipe, (javan_socket_length) sizeof(no_sigpipe)) != 0) {
            javan_panic("HTTP response signal configuration failed");
        }
        #endif
        int written = 0;
        while (written < length) {
            int flags = 0;
            #if defined(MSG_NOSIGNAL)
            flags = MSG_NOSIGNAL;
            #endif
            int chunk = javan_socket_native_send(socket->fd, bytes + written, length - written, flags);
            if (chunk <= 0) {
                javan_panic("HTTP response write failed");
            }
            written += chunk;
        }
        }

        static void javan_http_server_write_not_found(javan_socket* socket) {
            static const char response[] = "HTTP/1.1 404 Not Found\\r\\nContent-Length: 0\\r\\nConnection: close\\r\\n\\r\\n";
            javan_http_server_write_all(socket, response, (int) (sizeof(response) - 1U));
        }

        void* javan_http_server_create(void* address_value, int backlog) {
            javan_inet_socket_address* address = javan_inet_socket_address_checked(address_value);
            void* listener = NULL;
            void* server_value = NULL;
            void** roots[] = { &address_value, &listener, &server_value };
            javan_root_frame_push(roots, 3);
            listener = javan_server_socket_bind_config((void*) address->address->host_address, address->port, backlog);
            javan_http_server_value* server = (javan_http_server_value*) javan_alloc(sizeof(javan_http_server_value));
            server_value = (void*) server;
            server->magic = JAVAN_HTTP_SERVER_MAGIC;
            server->started = 0;
            server->stopped = 0;
            server->context_registered = 0;
            server->socket = (javan_server_socket*) listener;
            server->path = NULL;
            server->handler = NULL;
            server->worker = NULL;
            server->active_exchange = NULL;
            javan_update_runtime_allocation_kind(server_value, JAVAN_RUNTIME_KIND_HTTP_SERVER);
            javan_root_frame_pop(roots);
            return server_value;
        }

        void* javan_http_server_get_address(void* value) {
            javan_http_server_value* server = javan_http_server_checked(value);
            return javan_server_socket_get_local_socket_address((void*) server->socket);
        }

        void* javan_http_server_create_context(void* value, void* path, void* handler) {
            javan_http_server_value* server = javan_http_server_checked(value);
            if (path == NULL || handler == NULL || ((const char*) path)[0] != '/') {
                javan_panic("HttpServer context requires a path and handler");
            }
            void* server_root = value;
            void* path_root = path;
            void* handler_root = handler;
            void* context_value = NULL;
            void** roots[] = { &server_root, &path_root, &handler_root, &context_value };
            javan_root_frame_push(roots, 4);
            javan_runtime_lock_enter();
            if (server->started != 0 || server->stopped != 0 || server->context_registered != 0) {
                javan_runtime_lock_leave();
                javan_root_frame_pop(roots);
                javan_panic("HttpServer supports exactly one context before start");
            }
            server->path = (char*) path_root;
            server->handler = handler_root;
            server->context_registered = 1;
            javan_runtime_lock_leave();
            javan_http_context_value* context = (javan_http_context_value*) javan_alloc(sizeof(javan_http_context_value));
            context_value = (void*) context;
            context->magic = JAVAN_HTTP_CONTEXT_MAGIC;
            context->server = server;
            javan_update_runtime_allocation_kind(context_value, JAVAN_RUNTIME_KIND_HTTP_CONTEXT);
            javan_root_frame_pop(roots);
            return context_value;
        }

        void javan_http_server_start(void* value) {
            javan_http_server_value* server = javan_http_server_checked(value);
            void* worker = NULL;
            void* server_root = value;
            void** roots[] = { &server_root, &worker };
            javan_root_frame_push(roots, 2);
            javan_runtime_lock_enter();
            if (server->started != 0 || server->stopped != 0 || server->context_registered == 0) {
                javan_runtime_lock_leave();
                javan_root_frame_pop(roots);
                javan_panic("HttpServer requires one context and may only start once");
            }
            server->started = 1;
            javan_runtime_lock_leave();
            javan_thread_new_into(&worker);
            javan_thread_set_native_http_server(worker, server_root);
            javan_runtime_lock_enter();
            server = javan_http_server_checked(server_root);
            server->worker = worker;
            javan_runtime_lock_leave();
            javan_thread_start(worker);
            javan_root_frame_pop(roots);
        }

        static void javan_http_exchange_abort(void* value);

        static void javan_http_server_wait_for_worker(void* worker, int delay_seconds) {
            long long deadline = javan_system_nano_time() + (long long) delay_seconds * 1000000000LL;
            while (javan_thread_is_alive(worker) != 0 && javan_system_nano_time() < deadline) {
                javan_sleep_micros(5000UL);
            }
        }

        void javan_http_server_stop(void* value, int delay_seconds) {
            if (delay_seconds < 0) {
                javan_panic("negative HttpServer.stop delay");
            }
            javan_http_server_value* server = javan_http_server_checked(value);
            void* worker = NULL;
            javan_runtime_lock_enter();
            server->stopped = 1;
            worker = server->worker;
            javan_runtime_lock_leave();
            if (worker == NULL) {
                javan_server_socket_close((void*) server->socket);
            } else if (worker != javan_current_thread_object()) {
                if (delay_seconds > 0) {
                    javan_http_server_wait_for_worker(worker, delay_seconds);
                }
                void* active_exchange = NULL;
                void** roots[] = { &active_exchange };
                javan_root_frame_push(roots, 1);
                javan_runtime_lock_enter();
                active_exchange = server->active_exchange;
                javan_runtime_lock_leave();
                if (active_exchange != NULL) {
                    javan_http_exchange_abort(active_exchange);
                }
                javan_root_frame_pop(roots);
            }
        }

        static javan_socket_handle javan_http_server_accept(javan_http_server_value* server) {
            javan_server_socket* listener = server->socket;
            if (listener == NULL || javan_socket_handle_is_open(listener->fd) == 0) {
                return JAVAN_SOCKET_INVALID;
            }
            fd_set readable;
            FD_ZERO(&readable);
            FD_SET(listener->fd, &readable);
            struct timeval timeout;
            timeout.tv_sec = 0;
            timeout.tv_usec = 100000;
            int selected = javan_socket_native_select(listener->fd, &readable, NULL, &timeout);
            if (selected == 0 || (selected < 0 && javan_socket_last_error_is_interrupted() != 0)) {
                return JAVAN_SOCKET_INVALID;
            }
            if (selected < 0) {
                javan_panic("HttpServer accept wait failed");
            }
            javan_socket_handle accepted = javan_socket_native_accept(listener->fd);
            if (javan_socket_handle_is_open(accepted) == 0) {
                if (javan_socket_last_error_is_interrupted() != 0 || javan_socket_last_error_is_would_block() != 0) {
                    return JAVAN_SOCKET_INVALID;
                }
                javan_panic("HttpServer accept failed");
            }
            return accepted;
        }

        static void* javan_http_server_request_method(javan_socket* socket, const char* path) {
            char request[8192];
            int length = 0;
            while (length < (int) sizeof(request) - 1) {
                int read = javan_socket_native_receive(socket->fd, request + length, (int) sizeof(request) - length - 1);
                if (read <= 0) {
                    return NULL;
                }
                length += (int) read;
                int complete = 0;
                for (int index = 3; index < length; index++) {
                    if (request[index - 3] == '\\r'
                        && request[index - 2] == '\\n'
                        && request[index - 1] == '\\r'
                        && request[index] == '\\n') {
                        length = index + 1;
                        complete = 1;
                        break;
                    }
                }
                if (complete != 0) {
                    break;
                }
            }
            request[length] = '\\0';
            char* first_space = strchr(request, ' ');
            if (first_space == NULL) {
                return NULL;
            }
            const char* target = first_space + 1;
            const char* target_end = strchr(target, ' ');
            if (target_end == NULL) {
                return NULL;
            }
            int expected = (int) strlen(path);
            int actual = 0;
            while (target + actual < target_end && target[actual] != '?') {
                actual++;
            }
            if (actual != expected || strncmp(target, path, (size_t) expected) != 0) {
                return NULL;
            }
            *first_space = '\\0';
            return javan_string_from(request);
        }

        static javan_http_exchange_value* javan_http_exchange_new(javan_socket* socket, void* request_method) {
            javan_http_exchange_value* exchange = (javan_http_exchange_value*) javan_alloc(sizeof(javan_http_exchange_value));
            exchange->magic = JAVAN_HTTP_EXCHANGE_MAGIC;
            exchange->response_headers_sent = 0;
            exchange->closed = 0;
            exchange->chunked = 0;
            exchange->write_active = 0;
            exchange->close_requested = 0;
            exchange->response_length = -2LL;
            exchange->response_written = 0LL;
            exchange->socket = socket;
            exchange->request_method = request_method;
            exchange->response_body = NULL;
            javan_update_runtime_allocation_kind((void*) exchange, JAVAN_RUNTIME_KIND_HTTP_EXCHANGE);
            return exchange;
        }

        void javan_http_server_run(void* value) {
            javan_http_server_value* server = javan_http_server_checked(value);
            void* server_root = value;
            void* socket_value = NULL;
            void* exchange_value = NULL;
            void* request_method = NULL;
            void** roots[] = { &server_root, &socket_value, &exchange_value, &request_method };
            javan_root_frame_push(roots, 4);
            while (javan_http_server_stopped(server) == 0) {
                javan_socket_handle accepted = javan_http_server_accept(server);
                if (javan_socket_handle_is_open(accepted) == 0) {
                    continue;
                }
                javan_socket_wrap_connected_fd_into(&socket_value, accepted);
                javan_socket* socket = (javan_socket*) socket_value;
                if (javan_http_server_stopped(server) != 0) {
                    javan_socket_close(socket_value);
                } else {
                    request_method = javan_http_server_request_method(socket, server->path);
                    if (request_method != NULL) {
                        exchange_value = (void*) javan_http_exchange_new(socket, request_method);
                        javan_runtime_lock_enter();
                        server->active_exchange = exchange_value;
                        javan_runtime_lock_leave();
                        if (javan_http_server_stopped(server) == 0) {
                            javan_http_server_handle(server->handler, exchange_value);
                        }
                        javan_http_exchange_close(exchange_value);
                        javan_runtime_lock_enter();
                        server->active_exchange = NULL;
                        javan_runtime_lock_leave();
                    } else {
                        javan_http_server_write_not_found(socket);
                        javan_socket_close(socket_value);
                    }
                }
                socket_value = NULL;
                exchange_value = NULL;
                request_method = NULL;
            }
            javan_server_socket_close((void*) server->socket);
            javan_root_frame_pop(roots);
        }

        void* javan_http_exchange_get_request_method(void* value) {
            return javan_http_exchange_checked(value)->request_method;
        }

        void javan_http_exchange_send_response_headers(void* value, int status_code, long long response_length) {
            javan_http_exchange_value* exchange = javan_http_exchange_checked(value);
            if (status_code < 100 || status_code > 999 || response_length < -1LL) {
                javan_panic("invalid HttpExchange response headers");
            }
            const char* reason = status_code == 200 ? "OK" : status_code == 404 ? "Not Found" : "Response";
            char headers[256];
            int written = response_length > 0LL
                ? snprintf(headers, sizeof(headers), "HTTP/1.1 %d %s\\r\\nContent-Length: %lld\\r\\nConnection: close\\r\\n\\r\\n", status_code, reason, response_length)
                : response_length == 0LL
                    ? snprintf(headers, sizeof(headers), "HTTP/1.1 %d %s\\r\\nTransfer-Encoding: chunked\\r\\nConnection: close\\r\\n\\r\\n", status_code, reason)
                    : snprintf(headers, sizeof(headers), "HTTP/1.1 %d %s\\r\\nContent-Length: 0\\r\\nConnection: close\\r\\n\\r\\n", status_code, reason);
            if (written < 0 || written >= (int) sizeof(headers)) {
                javan_panic("HTTP response header overflow");
            }
            void* socket_value = NULL;
            javan_runtime_lock_enter();
            if (exchange->closed != 0 || exchange->close_requested != 0 || exchange->write_active != 0 || exchange->response_headers_sent != 0) {
                javan_runtime_lock_leave();
                javan_panic("invalid HttpExchange response headers");
            }
            exchange->response_headers_sent = 1;
            exchange->chunked = response_length == 0LL ? 1 : 0;
            exchange->response_length = response_length;
            exchange->write_active = 1;
            socket_value = (void*) exchange->socket;
            javan_runtime_lock_leave();
            javan_http_server_write_all((javan_socket*) socket_value, headers, written);
            javan_runtime_lock_enter();
            exchange->write_active = 0;
            if (exchange->close_requested != 0 && exchange->closed == 0) {
                exchange->closed = 1;
                socket_value = (void*) exchange->socket;
            } else {
                socket_value = NULL;
            }
            javan_runtime_lock_leave();
            if (socket_value != NULL) {
                javan_socket_close(socket_value);
            }
        }

        void* javan_http_exchange_output_stream(void* value) {
            javan_http_exchange_value* exchange = javan_http_exchange_checked(value);
            javan_runtime_lock_enter();
            int unavailable = exchange->closed != 0
                || exchange->close_requested != 0
                || exchange->response_headers_sent == 0
                || exchange->response_length < 0LL;
            void* existing = exchange->response_body;
            javan_runtime_lock_leave();
            if (unavailable != 0) {
                javan_panic("HttpExchange response body is unavailable");
            }
            if (existing != NULL) {
                return existing;
            }
            void* exchange_root = value;
            void* result = NULL;
            void** roots[] = { &exchange_root, &result };
            javan_root_frame_push(roots, 2);
            javan_http_exchange_output_stream_value* stream =
                (javan_http_exchange_output_stream_value*) javan_alloc(sizeof(javan_http_exchange_output_stream_value));
            result = (void*) stream;
            stream->magic = JAVAN_HTTP_EXCHANGE_OUTPUT_STREAM_MAGIC;
            stream->reserved0 = 0;
            stream->reserved1 = 0;
            stream->reserved2 = 0;
            stream->exchange = exchange;
            javan_update_runtime_allocation_kind(result, JAVAN_RUNTIME_KIND_HTTP_EXCHANGE_OUTPUT_STREAM);
            javan_runtime_lock_enter();
            if (exchange->closed != 0 || exchange->close_requested != 0) {
                javan_runtime_lock_leave();
                javan_root_frame_pop(roots);
                javan_panic("HttpExchange response body is unavailable");
            }
            if (exchange->response_body == NULL) {
                exchange->response_body = result;
            } else {
                result = exchange->response_body;
            }
            javan_runtime_lock_leave();
            javan_root_frame_pop(roots);
            return result;
        }

        static void javan_http_exchange_write_bytes(javan_http_exchange_value* exchange, const char* bytes, int length) {
            if (length < 0) {
                javan_panic("invalid HttpExchange response body write");
            }
            void* socket_value = NULL;
            int chunked = 0;
            javan_runtime_lock_enter();
            if (exchange->closed != 0 || exchange->close_requested != 0 || exchange->write_active != 0 || exchange->response_headers_sent == 0) {
                javan_runtime_lock_leave();
                javan_panic("invalid HttpExchange response body write");
            }
            if (exchange->response_length > 0LL && exchange->response_written + (long long) length > exchange->response_length) {
                javan_runtime_lock_leave();
                javan_panic("HttpExchange response exceeds declared length");
            }
            if (exchange->response_length < 0LL && length > 0) {
                javan_runtime_lock_leave();
                javan_panic("HttpExchange response has no body");
            }
            exchange->response_written += (long long) length;
            exchange->write_active = 1;
            chunked = exchange->chunked;
            socket_value = (void*) exchange->socket;
            javan_runtime_lock_leave();
            if (chunked != 0 && length > 0) {
                char prefix[32];
                int prefix_length = snprintf(prefix, sizeof(prefix), "%x\\r\\n", (unsigned int) length);
                if (prefix_length < 0 || prefix_length >= (int) sizeof(prefix)) {
                    javan_panic("HTTP chunk header overflow");
                }
                javan_http_server_write_all((javan_socket*) socket_value, prefix, prefix_length);
                javan_http_server_write_all((javan_socket*) socket_value, bytes, length);
                javan_http_server_write_all((javan_socket*) socket_value, "\\r\\n", 2);
            } else if (length > 0) {
                javan_http_server_write_all((javan_socket*) socket_value, bytes, length);
            }
            javan_runtime_lock_enter();
            exchange->write_active = 0;
            if (exchange->close_requested != 0 && exchange->closed == 0) {
                exchange->closed = 1;
                socket_value = (void*) exchange->socket;
            } else {
                socket_value = NULL;
            }
            javan_runtime_lock_leave();
            if (socket_value != NULL) {
                javan_socket_close(socket_value);
            }
        }

        void javan_http_exchange_output_stream_write(void* value, int byte_value) {
            javan_http_exchange_output_stream_value* stream = javan_http_exchange_output_stream_checked(value);
            char byte = (char) (byte_value & 0xff);
            javan_http_exchange_write_bytes(stream->exchange, &byte, 1);
        }

        void javan_http_exchange_output_stream_write_bytes(void* value, void* bytes_value) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_http_exchange_output_stream_write_bytes_range(value, bytes_value, 0, bytes->length);
        }

        void javan_http_exchange_output_stream_write_bytes_range(void* value, void* bytes_value, int offset, int length) {
            javan_http_exchange_output_stream_value* stream = javan_http_exchange_output_stream_checked(value);
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_socket_stream_range_checked(bytes, offset, length);
            javan_http_exchange_write_bytes(stream->exchange, (const char*) bytes->values + offset, length);
        }

        void javan_http_exchange_output_stream_flush(void* value) {
            (void) javan_http_exchange_output_stream_checked(value);
        }

        void javan_http_exchange_output_stream_close(void* value) {
            javan_http_exchange_output_stream_value* stream = javan_http_exchange_output_stream_checked(value);
            javan_http_exchange_close((void*) stream->exchange);
        }

        static void javan_http_exchange_abort(void* value) {
            javan_http_exchange_value* exchange = javan_http_exchange_checked(value);
            void* socket_value = NULL;
            javan_runtime_lock_enter();
            if (exchange->closed == 0) {
                exchange->close_requested = 1;
                if (exchange->write_active == 0) {
                    exchange->closed = 1;
                    socket_value = (void*) exchange->socket;
                }
            }
            javan_runtime_lock_leave();
            if (socket_value != NULL) {
                javan_socket_close(socket_value);
            }
        }

        void javan_http_exchange_close(void* value) {
            javan_http_exchange_value* exchange = javan_http_exchange_checked(value);
            void* socket_value = NULL;
            int chunked = 0;
            javan_runtime_lock_enter();
            if (exchange->closed != 0) {
                javan_runtime_lock_leave();
                return;
            }
            if (exchange->write_active != 0) {
                exchange->close_requested = 1;
                javan_runtime_lock_leave();
                return;
            }
            if (exchange->response_headers_sent != 0) {
                if (exchange->chunked == 0 && exchange->response_length > 0LL && exchange->response_written != exchange->response_length) {
                    javan_runtime_lock_leave();
                    javan_panic("HttpExchange response does not match declared length");
                }
                chunked = exchange->chunked;
            }
            exchange->closed = 1;
            exchange->write_active = 1;
            socket_value = (void*) exchange->socket;
            javan_runtime_lock_leave();
            if (chunked != 0) {
                javan_http_server_write_all((javan_socket*) socket_value, "0\\r\\n\\r\\n", 5);
            }
            javan_socket_close(socket_value);
            javan_runtime_lock_enter();
            exchange->write_active = 0;
            javan_runtime_lock_leave();
        }
        """;

    private RuntimeSourcePlatformSection() {
    }

    static String tail() {
        return SOURCE_TAIL_A.concat(SOURCE_TAIL_B).concat(SOURCE_TAIL_SOCKET).concat(SOURCE_TAIL_C);
    }
}
