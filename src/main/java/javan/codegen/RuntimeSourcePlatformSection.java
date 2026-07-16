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
            return builder;
        }

        static void javan_stringbuilder_ensure_capacity(javan_string_builder* builder, int required) {
            if (required < 0) {
                javan_panic("string builder length overflow");
            }
            if (required == INT_MAX) {
                javan_panic("string builder length overflow");
            }
            if (builder->values != NULL && required <= builder->capacity) {
                return;
            }
            int next_capacity = builder->capacity;
            if (next_capacity <= 0) {
                next_capacity = 16;
            }
            if (builder->capacity > 0) {
                while (next_capacity < required) {
                    if (next_capacity > (INT_MAX - 2) / 2) {
                        javan_panic("string builder length overflow");
                    }
                    next_capacity = next_capacity * 2 + 2;
                }
            } else if (required > next_capacity) {
                while (next_capacity < required) {
                    if (next_capacity > (INT_MAX - 2) / 2) {
                        javan_panic("string builder length overflow");
                    }
                    next_capacity = next_capacity * 2 + 2;
                }
            }
            if (next_capacity < 0) {
                javan_panic("string builder length overflow");
            }
            int old_capacity = builder->capacity;
            int created_buffer = builder->values == NULL;
            void** javan_builder_growth_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_growth_roots, 1);
            char* next = (char*) javan_realloc_owned_buffer(builder->values, (unsigned long) next_capacity + 1UL);
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
            if (created_buffer != 0) {
                memset(next, 0, (unsigned long) next_capacity + 1UL);
            } else if (next_capacity > old_capacity) {
                memset(next + old_capacity + 1, 0, (unsigned long) (next_capacity - old_capacity));
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

        static void javan_stringbuilder_insert_bytes(javan_string_builder* builder, int index, const char* value) {
            if (index < 0 || index > builder->length) {
                javan_panic("string builder insert index out of bounds");
            }
            const char* source = value == NULL ? "null" : value;
            unsigned long length = strlen(source);
            if (length > (unsigned long) INT_MAX || builder->length > INT_MAX - (int) length) {
                javan_panic("string builder length overflow");
            }
            void* source_root = (void*) source;
            void** javan_builder_insert_roots[] = {
                (void**) &builder,
                (void**) &source_root
            };
            javan_root_frame_push(javan_builder_insert_roots, 2);
            int insert_length = (int) length;
            javan_stringbuilder_ensure_capacity(builder, builder->length + insert_length);
            memmove(
                builder->values + index + insert_length,
                builder->values + index,
                (unsigned long) (builder->length - index + 1)
            );
            memcpy(builder->values + index, (const char*) source_root, length);
            builder->length += insert_length;
            javan_root_frame_pop(javan_builder_insert_roots);
        }

        void* javan_stringbuilder_new(void) {
            javan_string_builder* builder = (javan_string_builder*) javan_alloc(sizeof(javan_string_builder));
            builder->magic = JAVAN_STRING_BUILDER_MAGIC;
            builder->length = 0;
            builder->capacity = 16;
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
            char* next = (char*) javan_realloc_owned_buffer(builder->values, (unsigned long) capacity + 1UL);
            if (next == NULL) {
                javan_panic("out of memory");
            }
            memset(next + builder->capacity + 1, 0, (unsigned long) (capacity - builder->capacity));
            builder->values = next;
            builder->capacity = capacity;
            javan_heap_maybe_validate();
            javan_root_frame_pop(javan_builder_owner_roots);
        }

        void* javan_stringbuilder_append_string(void* builder_value, void* value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_append_bytes(builder, (const char*) value);
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
            javan_stringbuilder_append_bytes(builder, (const char*) text);
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

        void* javan_stringbuilder_append_float(void* builder_value, float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            javan_stringbuilder_append_bytes(javan_stringbuilder_checked(builder_value), buffer);
            return builder_value;
        }

        void* javan_stringbuilder_append_double(void* builder_value, double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
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

        int javan_stringbuilder_char_at(void* builder_value, int index) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (index < 0 || index >= builder->length) {
                javan_panic("string builder index out of bounds");
            }
            return (unsigned char) builder->values[index];
        }

        int javan_char_sequence_length(void* value) {
            if (value == NULL) {
                javan_panic("null CharSequence");
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL || node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return javan_string_length((const char*) value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_length(value);
            }
            javan_panic("unsupported CharSequence runtime");
            return 0;
        }

        int javan_char_sequence_char_at(void* value, int index) {
            if (value == NULL) {
                javan_panic("null CharSequence");
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL || node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return javan_string_char_at((const char*) value, index);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_char_at(value, index);
            }
            javan_panic("unsupported CharSequence runtime");
            return 0;
        }

        int javan_character_is_whitespace(int value) {
            return value == 0x20
                || (value >= 0x09 && value <= 0x0d)
                || (value >= 0x1c && value <= 0x1f);
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
                int left = (unsigned char) builder->values[index];
                int right = (unsigned char) other->values[index];
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
            memmove(
                builder->values + start,
                builder->values + effective_end,
                (unsigned long) (builder->length - effective_end + 1)
            );
            builder->length -= effective_end - start;
            return builder_value;
        }

        void* javan_stringbuilder_delete_char_at(void* builder_value, int index) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (index < 0 || index >= builder->length) {
                javan_panic("string builder delete char index out of bounds");
            }
            memmove(
                builder->values + index,
                builder->values + index + 1,
                (unsigned long) (builder->length - index)
            );
            builder->length -= 1;
            return builder_value;
        }

        void* javan_stringbuilder_insert_boolean(void* builder_value, int index, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_insert_bytes(builder, index, value == 0 ? "false" : "true");
            return builder_value;
        }

        void* javan_stringbuilder_insert_int(void* builder_value, int index, int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            javan_stringbuilder_insert_bytes(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_long(void* builder_value, int index, long long value) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            javan_stringbuilder_insert_bytes(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_float(void* builder_value, int index, float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            javan_stringbuilder_insert_bytes(javan_stringbuilder_checked(builder_value), index, buffer);
            return builder_value;
        }

        void* javan_stringbuilder_insert_double(void* builder_value, int index, double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            javan_stringbuilder_insert_bytes(javan_stringbuilder_checked(builder_value), index, buffer);
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
            javan_stringbuilder_insert_bytes(builder, index, (const char*) text);
            javan_root_frame_pop(javan_builder_insert_chars_roots);
            return builder_value;
        }

        void* javan_stringbuilder_insert_chars(void* builder_value, int index, void* chars_value) {
            return javan_stringbuilder_insert_chars_range(builder_value, index, chars_value, 0, javan_array_length(chars_value));
        }

        void* javan_stringbuilder_insert_string(void* builder_value, int index, void* value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            javan_stringbuilder_insert_bytes(builder, index, (const char*) value);
            return builder_value;
        }

        void* javan_stringbuilder_insert_char(void* builder_value, int index, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            char buffer[2];
            buffer[0] = (char) value;
            buffer[1] = '\\0';
            javan_stringbuilder_insert_bytes(builder, index, buffer);
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
            for (int left = 0, right = builder->length - 1; left < right; left++, right--) {
                char swap = builder->values[left];
                builder->values[left] = builder->values[right];
                builder->values[right] = swap;
            }
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
            javan_stringbuilder_ensure_capacity(builder, minimum_capacity);
            javan_root_frame_pop(javan_builder_ensure_capacity_roots);
        }

        void javan_stringbuilder_trim_to_size(void* builder_value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            int target_capacity = builder->length;
            if (builder->capacity == target_capacity) {
                return;
            }
            if (target_capacity == 0) {
                javan_free_owned_runtime_buffer((void*) builder->values);
                builder->values = NULL;
                builder->capacity = 0;
                return;
            }
            void** javan_builder_trim_roots[] = {
                (void**) &builder
            };
            javan_root_frame_push(javan_builder_trim_roots, 1);
            char* next = (char*) javan_realloc_owned_buffer(builder->values, (unsigned long) target_capacity + 1UL);
            if (next == NULL) {
                javan_panic("out of memory");
            }
            builder->values = next;
            builder->capacity = target_capacity;
            builder->values[builder->length] = '\\0';
            javan_heap_maybe_validate();
            javan_root_frame_pop(javan_builder_trim_roots);
        }

        void javan_stringbuilder_set_char_at(void* builder_value, int index, int value) {
            javan_string_builder* builder = javan_stringbuilder_checked(builder_value);
            if (index < 0 || index >= builder->length) {
                javan_panic("string builder set char index out of bounds");
            }
            builder->values[index] = (char) value;
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
            void* address_root = (void*) address_value;
            void* name_root = (void*) name_value;
            void* canonical_root = (void*) canonical_value;
            void** javan_inet_address_roots[] = {
                (void**) &address_root,
                (void**) &name_root,
                (void**) &canonical_root
            };
            javan_root_frame_push(javan_inet_address_roots, 3);
            javan_inet_address* address = (javan_inet_address*) javan_alloc(sizeof(javan_inet_address));
            void* object_root = (void*) address;
            void** javan_inet_address_object_roots[] = {
                (void**) &address_root,
                (void**) &name_root,
                (void**) &canonical_root,
                (void**) &object_root
            };
            javan_root_frame_push(javan_inet_address_object_roots, 4);
            address->magic = JAVAN_INET_ADDRESS_MAGIC;
            address->reserved0 = 0;
            address->reserved1 = 0;
            address->reserved2 = 0;
            address->host_address = (char*) javan_string_copy((const char*) address_root);
            address->host_name = (char*) javan_string_copy((const char*) name_root);
            address->canonical_host_name = (char*) javan_string_copy((const char*) canonical_root);
            javan_update_runtime_allocation_kind((void*) address, JAVAN_RUNTIME_KIND_INET_ADDRESS);
            javan_root_frame_pop(javan_inet_address_object_roots);
            javan_root_frame_pop(javan_inet_address_roots);
            return address;
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
        static int javan_socket_getsockopt_int(int fd, int level, int option_name, const char* message);
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
                if (inet_ntop(AF_INET, (const void*) &address.sin_addr, host_address, (socklen_t) host_address_size) == NULL) {
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
            array_root = javan_object_array_new(1);
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

        static int javan_socket_default_buffer_size(int option_name, const char* message) {
        #if defined(_WIN32)
            (void) option_name;
            (void) message;
            javan_socket_runtime_unsupported();
            return 8192;
        #else
            int fd = socket(AF_INET, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic(message);
            }
            int value = javan_socket_getsockopt_int(fd, SOL_SOCKET, option_name, message);
            javan_socket_native_close(fd);
            return value <= 0 ? 8192 : value;
        #endif
        }

        static int javan_server_socket_backlog_checked(int backlog) {
            if (backlog <= 0) {
                return 16;
            }
            return backlog;
        }

        static void javan_socket_host_checked(const char* host, struct sockaddr_storage* address, socklen_t* address_length, int port) {
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
            socklen_t* address_length,
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
                if (inet_ntop(AF_INET, (const void*) &address4->sin_addr, host, sizeof(host)) == NULL) {
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

        static int javan_socket_getsockopt_flag(int fd, int level, int option_name, const char* message) {
            int value = 0;
            socklen_t length = (socklen_t) sizeof(value);
            if (getsockopt(fd, level, option_name, (void*) &value, &length) != 0) {
                javan_panic(message);
            }
            return value == 0 ? 0 : 1;
        }

        static void javan_socket_setsockopt_flag(int fd, int level, int option_name, int enabled, const char* message) {
            int value = enabled == 0 ? 0 : 1;
            if (setsockopt(fd, level, option_name, (const void*) &value, (socklen_t) sizeof(value)) != 0) {
                javan_panic(message);
            }
        }

        static int javan_socket_getsockopt_int(int fd, int level, int option_name, const char* message) {
            int value = 0;
            socklen_t length = (socklen_t) sizeof(value);
            if (getsockopt(fd, level, option_name, (void*) &value, &length) != 0) {
                javan_panic(message);
            }
            if (value < 0) {
                javan_panic(message);
            }
            return value;
        }

        static int javan_socket_buffer_size_checked(int size) {
            if (size <= 0) {
                javan_panic("non-positive socket buffer size");
            }
            return size;
        }

        static void javan_socket_setsockopt_int(int fd, int level, int option_name, int value, const char* message) {
            if (setsockopt(fd, level, option_name, (const void*) &value, (socklen_t) sizeof(value)) != 0) {
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

        static int javan_socket_getsockopt_linger(int fd, const char* message) {
            struct linger value;
            socklen_t length = (socklen_t) sizeof(value);
            memset(&value, 0, sizeof(value));
            if (getsockopt(fd, SOL_SOCKET, SO_LINGER, (void*) &value, &length) != 0) {
                javan_panic(message);
            }
            if (value.l_onoff == 0) {
                return -1;
            }
            if (value.l_linger < 0) {
                javan_panic(message);
            }
            return value.l_linger > 65535 ? 65535 : value.l_linger;
        }

        static void javan_socket_setsockopt_linger(int fd, int enabled, int linger_seconds, const char* message) {
            struct linger value;
            value.l_onoff = enabled == 0 ? 0 : 1;
            value.l_linger = enabled == 0 ? 0 : linger_seconds;
            if (setsockopt(fd, SOL_SOCKET, SO_LINGER, (const void*) &value, (socklen_t) sizeof(value)) != 0) {
                javan_panic(message);
            }
        }

        static int javan_socket_traffic_class_level(int fd, int* option_name_out) {
            struct sockaddr_storage local_address;
            socklen_t local_length = sizeof(local_address);
            if (getsockname(fd, (struct sockaddr*) &local_address, &local_length) != 0) {
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

        static int javan_socket_getsockopt_traffic_class(int fd, const char* message) {
            int option_name = 0;
            int level = javan_socket_traffic_class_level(fd, &option_name);
            return javan_socket_getsockopt_int(fd, level, option_name, message);
        }

        static void javan_socket_setsockopt_traffic_class(int fd, int traffic_class, const char* message) {
            int option_name = 0;
            int level = javan_socket_traffic_class_level(fd, &option_name);
            javan_socket_setsockopt_int(fd, level, option_name, traffic_class, message);
        }

        static void javan_socket_apply_receive_timeout(int fd, int timeout_millis, const char* message) {
            struct timeval timeout;
            timeout.tv_sec = (time_t) (timeout_millis / 1000);
            timeout.tv_usec = (suseconds_t) ((timeout_millis % 1000) * 1000);
            if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (const void*) &timeout, (socklen_t) sizeof(timeout)) != 0) {
                javan_panic(message);
            }
        }

        static void javan_socket_wait_readable(int fd, int timeout_millis, const char* timeout_message, const char* wait_message) {
            if (timeout_millis <= 0) {
                return;
            }
            fd_set read_set;
            FD_ZERO(&read_set);
            FD_SET(fd, &read_set);
            struct timeval timeout;
            timeout.tv_sec = (time_t) (timeout_millis / 1000);
            timeout.tv_usec = (suseconds_t) ((timeout_millis % 1000) * 1000);
            int ready = select(fd + 1, &read_set, NULL, NULL, &timeout);
            if (ready == 0) {
                javan_panic(timeout_message);
            }
            if (ready < 0) {
                javan_panic(wait_message);
            }
        }

        static void javan_socket_populate_names(int fd, void** local_address_out, int* local_port_out, void** remote_address_out, int* remote_port_out) {
            struct sockaddr_storage local_address;
            socklen_t local_length = sizeof(local_address);
            if (getsockname(fd, (struct sockaddr*) &local_address, &local_length) != 0) {
                javan_panic("socket local address lookup failed");
            }
            struct sockaddr_storage remote_address;
            socklen_t remote_length = sizeof(remote_address);
            if (getpeername(fd, (struct sockaddr*) &remote_address, &remote_length) != 0) {
                javan_panic("socket remote address lookup failed");
            }
            *local_address_out = javan_inet_address_from_sockaddr((const struct sockaddr*) &local_address);
            *remote_address_out = javan_inet_address_from_sockaddr((const struct sockaddr*) &remote_address);
            *local_port_out = javan_socket_port_from_sockaddr((const struct sockaddr*) &local_address);
            *remote_port_out = javan_socket_port_from_sockaddr((const struct sockaddr*) &remote_address);
        }

        static void javan_socket_populate_options(int fd, int* tcp_no_delay_out, int* keep_alive_out, int* reuse_address_out, int* oob_inline_out, int* traffic_class_out) {
            *tcp_no_delay_out = javan_socket_getsockopt_flag(fd, IPPROTO_TCP, TCP_NODELAY, "socket TCP_NODELAY lookup failed");
            *keep_alive_out = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_KEEPALIVE, "socket SO_KEEPALIVE lookup failed");
            *reuse_address_out = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_REUSEADDR, "socket SO_REUSEADDR lookup failed");
            *oob_inline_out = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_OOBINLINE, "socket SO_OOBINLINE lookup failed");
            *traffic_class_out = javan_socket_getsockopt_traffic_class(fd, "socket traffic class lookup failed");
        }

        void* javan_socket_new(void) {
            void* local_address = javan_inet_address_new(NULL, NULL, NULL);
            void* local_address_root = local_address;
            void** javan_socket_new_roots[] = {
                (void**) &local_address_root
            };
            javan_root_frame_push(javan_socket_new_roots, 1);
            javan_socket* socket = (javan_socket*) javan_alloc(sizeof(javan_socket));
            void* socket_root = (void*) socket;
            void** javan_socket_new_owner_roots[] = {
                (void**) &local_address_root,
                (void**) &socket_root
            };
            javan_root_frame_push(javan_socket_new_owner_roots, 2);
            socket = (javan_socket*) socket_root;
            socket->magic = JAVAN_SOCKET_MAGIC;
            socket->fd = -1;
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
            socket->local_address = (javan_inet_address*) local_address_root;
            socket->remote_address = NULL;
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SOCKET);
            javan_root_frame_pop(javan_socket_new_owner_roots);
            javan_root_frame_pop(javan_socket_new_roots);
            return socket;
        }

        static void javan_socket_connect_native_timeout(int fd, const struct sockaddr* address, socklen_t address_length, int timeout_millis) {
            int timeout = javan_socket_timeout_checked(timeout_millis);
            if (timeout == 0) {
                if (connect(fd, address, address_length) != 0) {
                    javan_panic("socket connect failed");
                }
                return;
            }
            int flags = fcntl(fd, F_GETFL, 0);
            if (flags < 0) {
                javan_panic("socket connect flags lookup failed");
            }
            if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) != 0) {
                javan_panic("socket connect nonblocking setup failed");
            }
            int result = connect(fd, address, address_length);
            if (result != 0 && errno != EINPROGRESS) {
                fcntl(fd, F_SETFL, flags);
                javan_panic("socket connect failed");
            }
            if (result != 0) {
                fd_set write_set;
                FD_ZERO(&write_set);
                FD_SET(fd, &write_set);
                struct timeval timeout_value;
                timeout_value.tv_sec = (time_t) (timeout / 1000);
                timeout_value.tv_usec = (suseconds_t) ((timeout % 1000) * 1000);
                int ready = select(fd + 1, NULL, &write_set, NULL, &timeout_value);
                if (ready == 0) {
                    fcntl(fd, F_SETFL, flags);
                    javan_panic("socket connect timed out");
                }
                if (ready < 0) {
                    fcntl(fd, F_SETFL, flags);
                    javan_panic("socket connect wait failed");
                }
                int error = 0;
                socklen_t error_length = (socklen_t) sizeof(error);
                if (getsockopt(fd, SOL_SOCKET, SO_ERROR, (void*) &error, &error_length) != 0) {
                    fcntl(fd, F_SETFL, flags);
                    javan_panic("socket connect state lookup failed");
                }
                if (error != 0) {
                    fcntl(fd, F_SETFL, flags);
                    javan_panic("socket connect failed");
                }
            }
            if (fcntl(fd, F_SETFL, flags) != 0) {
                javan_panic("socket connect blocking restore failed");
            }
        }

        static void javan_socket_assign_connected_fd(void* socket_value, int fd) {
        #if defined(_WIN32)
            (void) socket_value;
            (void) fd;
            javan_socket_runtime_unsupported();
        #else
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
            receive_buffer_size = javan_socket_getsockopt_int(fd, SOL_SOCKET, SO_RCVBUF, "socket SO_RCVBUF lookup failed");
            send_buffer_size = javan_socket_getsockopt_int(fd, SOL_SOCKET, SO_SNDBUF, "socket SO_SNDBUF lookup failed");
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
            javan_root_frame_pop(javan_socket_assign_roots);
        #endif
        }

        static void* javan_socket_wrap_connected_fd(int fd) {
        #if defined(_WIN32)
            (void) fd;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            void* socket = javan_socket_new();
            void* socket_root = socket;
            void** javan_socket_wrap_roots[] = {
                (void**) &socket_root
            };
            javan_root_frame_push(javan_socket_wrap_roots, 1);
            javan_socket_assign_connected_fd(socket_root, fd);
            javan_root_frame_pop(javan_socket_wrap_roots);
            return socket_root;
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
        #if defined(_WIN32)
            (void) host_value;
            (void) port;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_storage address;
            socklen_t address_length = 0;
            javan_socket_host_checked(host, &address, &address_length, port);
            int fd = socket(((struct sockaddr*) &address)->sa_family, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("socket open failed");
            }
            javan_socket_connect_native_timeout(fd, (struct sockaddr*) &address, address_length, 0);
            return javan_socket_wrap_connected_fd(fd);
        #endif
        }

        static void* javan_socket_connect_host_timeout(void* host_value, int port, int timeout_millis) {
        #if defined(_WIN32)
            (void) host_value;
            (void) port;
            (void) timeout_millis;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_storage address;
            socklen_t address_length = 0;
            javan_socket_host_checked(host, &address, &address_length, port);
            int fd = socket(((struct sockaddr*) &address)->sa_family, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("socket open failed");
            }
            javan_socket_connect_native_timeout(fd, (struct sockaddr*) &address, address_length, timeout_millis);
            return javan_socket_wrap_connected_fd(fd);
        #endif
        }

        void* javan_socket_connect_host_config(void* host_value, int port, void* local_address_value, int local_port) {
        #if defined(_WIN32)
            (void) host_value;
            (void) port;
            (void) local_address_value;
            (void) local_port;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            const char* local_host = local_address_value == NULL
                ? NULL
                : (const char*) javan_inet_address_checked(local_address_value)->host_address;
            struct sockaddr_storage remote_address;
            socklen_t remote_length = 0;
            javan_socket_host_checked(host, &remote_address, &remote_length, port);
            int fd = socket(((struct sockaddr*) &remote_address)->sa_family, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("socket open failed");
            }
            if (local_host != NULL || local_port != 0) {
                struct sockaddr_storage local_address;
                socklen_t local_length = 0;
                javan_socket_local_bind_checked(
                    ((struct sockaddr*) &remote_address)->sa_family,
                    local_host,
                    &local_address,
                    &local_length,
                    local_port
                );
                if (bind(fd, (struct sockaddr*) &local_address, local_length) != 0) {
                    javan_socket_native_close(fd);
                    javan_panic("socket local bind failed");
                }
            }
            javan_socket_connect_native_timeout(fd, (struct sockaddr*) &remote_address, remote_length, 0);
            return javan_socket_wrap_connected_fd(fd);
        #endif
        }

        void* javan_socket_connect_address_config(void* remote_address_value, int port, void* local_address_value, int local_port) {
        #if defined(_WIN32)
            (void) remote_address_value;
            (void) port;
            (void) local_address_value;
            (void) local_port;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            if (remote_address_value == NULL) {
                javan_panic("null inet address");
            }
            void* remote_host = javan_inet_address_checked(remote_address_value)->host_address;
            return javan_socket_connect_host_config(remote_host, port, local_address_value, local_port);
        #endif
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
        #if defined(_WIN32)
            (void) value;
            (void) timeout_millis;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int timeout = javan_socket_timeout_checked(timeout_millis);
            if (socket->fd >= 0) {
                javan_socket_apply_receive_timeout(socket->fd, timeout, "socket SO_RCVTIMEO update failed");
            }
            socket->so_timeout = timeout;
        #endif
        }

        int javan_socket_get_so_linger(void* value) {
            return javan_socket_checked(value)->so_linger;
        }

        void javan_socket_set_so_linger(void* value, int enabled, int linger_seconds) {
        #if defined(_WIN32)
            (void) value;
            (void) enabled;
            (void) linger_seconds;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (enabled == 0) {
                if (socket->fd >= 0) {
                    javan_socket_setsockopt_linger(socket->fd, 0, 0, "socket SO_LINGER update failed");
                }
                socket->so_linger = -1;
                return;
            }
            int checked = javan_socket_linger_checked(linger_seconds);
            if (socket->fd >= 0) {
                javan_socket_setsockopt_linger(socket->fd, 1, checked, "socket SO_LINGER update failed");
            }
            socket->so_linger = checked;
        #endif
        }

        int javan_socket_get_oob_inline(void* value) {
            return javan_socket_checked(value)->oob_inline;
        }

        void javan_socket_set_oob_inline(void* value, int enabled) {
        #if defined(_WIN32)
            (void) value;
            (void) enabled;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->fd >= 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_OOBINLINE, enabled, "socket SO_OOBINLINE update failed");
            }
            socket->oob_inline = enabled == 0 ? 0 : 1;
        #endif
        }

        int javan_socket_get_traffic_class(void* value) {
            return javan_socket_checked(value)->traffic_class;
        }

        void javan_socket_set_traffic_class(void* value, int traffic_class) {
        #if defined(_WIN32)
            (void) value;
            (void) traffic_class;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int checked = javan_socket_traffic_class_checked(traffic_class);
            if (socket->fd >= 0) {
                javan_socket_setsockopt_traffic_class(socket->fd, checked, "socket traffic class update failed");
            }
            socket->traffic_class = checked;
        #endif
        }

        int javan_socket_get_tcp_no_delay(void* value) {
            return javan_socket_checked(value)->tcp_no_delay;
        }

        void javan_socket_set_tcp_no_delay(void* value, int enabled) {
        #if defined(_WIN32)
            (void) value;
            (void) enabled;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->fd >= 0) {
                javan_socket_setsockopt_flag(socket->fd, IPPROTO_TCP, TCP_NODELAY, enabled, "socket TCP_NODELAY update failed");
            }
            socket->tcp_no_delay = enabled == 0 ? 0 : 1;
        #endif
        }

        int javan_socket_get_keep_alive(void* value) {
            return javan_socket_checked(value)->keep_alive;
        }

        void javan_socket_set_keep_alive(void* value, int enabled) {
        #if defined(_WIN32)
            (void) value;
            (void) enabled;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->fd >= 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_KEEPALIVE, enabled, "socket SO_KEEPALIVE update failed");
            }
            socket->keep_alive = enabled == 0 ? 0 : 1;
        #endif
        }

        int javan_socket_get_reuse_address(void* value) {
            return javan_socket_checked(value)->reuse_address;
        }

        void javan_socket_set_reuse_address(void* value, int enabled) {
        #if defined(_WIN32)
            (void) value;
            (void) enabled;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->fd >= 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_REUSEADDR, enabled, "socket SO_REUSEADDR update failed");
            }
            socket->reuse_address = enabled == 0 ? 0 : 1;
        #endif
        }

        int javan_socket_get_receive_buffer_size(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return 0;
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->fd < 0) {
                return socket->receive_buffer_size;
            }
            return javan_socket_getsockopt_int(socket->fd, SOL_SOCKET, SO_RCVBUF, "socket SO_RCVBUF lookup failed");
        #endif
        }

        void javan_socket_set_receive_buffer_size(void* value, int size) {
        #if defined(_WIN32)
            (void) value;
            (void) size;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int checked = javan_socket_buffer_size_checked(size);
            if (socket->fd >= 0) {
                javan_socket_setsockopt_int(socket->fd, SOL_SOCKET, SO_RCVBUF, checked, "socket SO_RCVBUF update failed");
            }
            socket->receive_buffer_size = checked;
        #endif
        }

        int javan_socket_get_send_buffer_size(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return 0;
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            if (socket->fd < 0) {
                return socket->send_buffer_size;
            }
            return javan_socket_getsockopt_int(socket->fd, SOL_SOCKET, SO_SNDBUF, "socket SO_SNDBUF lookup failed");
        #endif
        }

        void javan_socket_set_send_buffer_size(void* value, int size) {
        #if defined(_WIN32)
            (void) value;
            (void) size;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("socket is closed");
            }
            int checked = javan_socket_buffer_size_checked(size);
            if (socket->fd >= 0) {
                javan_socket_setsockopt_int(socket->fd, SOL_SOCKET, SO_SNDBUF, checked, "socket SO_SNDBUF update failed");
            }
            socket->send_buffer_size = checked;
        #endif
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
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return 0;
        #else
            javan_socket_input_stream_value* stream = javan_socket_input_stream_checked(value);
            javan_socket* socket = javan_socket_open_checked((void*) stream->socket);
            if (socket->input_shutdown != 0) {
                javan_panic("socket input is shutdown");
            }
            javan_socket_wait_readable(socket->fd, socket->so_timeout, "socket read timed out", "socket read wait failed");
            unsigned char byte = 0;
            ssize_t result = recv(socket->fd, &byte, 1, 0);
            if (result < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    javan_panic("socket read timed out");
                }
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
            if (socket->input_shutdown != 0) {
                javan_panic("socket input is shutdown");
            }
            javan_socket_wait_readable(socket->fd, socket->so_timeout, "socket read timed out", "socket read wait failed");
            ssize_t result = recv(socket->fd, bytes->values + offset, (size_t) length, 0);
            if (result < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    javan_panic("socket read timed out");
                }
                javan_panic("socket read failed");
            }
            if (result == 0) {
                return -1;
            }
            return (int) result;
        #endif
        }

        void javan_socket_shutdown_input(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_open_checked(value);
            if (socket->input_shutdown != 0) {
                javan_panic("socket input is already shutdown");
            }
            if (shutdown(socket->fd, SHUT_RD) != 0) {
                javan_panic("socket shutdown input failed");
            }
            socket->input_shutdown = 1;
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
            if (socket->output_shutdown != 0) {
                javan_panic("socket output is shutdown");
            }
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
            if (socket->output_shutdown != 0) {
                javan_panic("socket output is shutdown");
            }
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

        void javan_socket_shutdown_output(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
        #else
            javan_socket* socket = javan_socket_open_checked(value);
            if (socket->output_shutdown != 0) {
                javan_panic("socket output is already shutdown");
            }
            if (shutdown(socket->fd, SHUT_WR) != 0) {
                javan_panic("socket shutdown output failed");
            }
            socket->output_shutdown = 1;
        #endif
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
            if (socket->bound != 0) {
                socket->input_shutdown = 1;
                socket->output_shutdown = 1;
            }
            socket->closed = 1;
        }

        void* javan_server_socket_new(void) {
            javan_server_socket* socket = (javan_server_socket*) javan_alloc(sizeof(javan_server_socket));
            socket->magic = JAVAN_SERVER_SOCKET_MAGIC;
            socket->fd = -1;
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
        #if defined(_WIN32)
            (void) host_value;
            (void) port;
            (void) backlog;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            const char* host = host_value == NULL ? "localhost" : (const char*) host_value;
            struct sockaddr_storage address;
            socklen_t address_length = 0;
            javan_socket_host_checked(host, &address, &address_length, port);
            int fd = socket(((struct sockaddr*) &address)->sa_family, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("server socket open failed");
            }
            javan_socket_setsockopt_flag(fd, SOL_SOCKET, SO_REUSEADDR, 1, "server socket SO_REUSEADDR update failed");
            if (bind(fd, (struct sockaddr*) &address, address_length) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket bind failed");
            }
            if (listen(fd, javan_server_socket_backlog_checked(backlog)) != 0) {
                javan_socket_native_close(fd);
                javan_panic("server socket listen failed");
            }
            struct sockaddr_storage bound;
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
            socket->receive_buffer_size = javan_socket_getsockopt_int(fd, SOL_SOCKET, SO_RCVBUF, "server socket SO_RCVBUF lookup failed");
            socket->local_address = (javan_inet_address*) local_address;
            javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SERVER_SOCKET);
            javan_root_frame_pop(javan_server_socket_owner_roots);
            javan_root_frame_pop(javan_server_socket_roots);
            return socket;
        #endif
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
        #if defined(_WIN32)
            (void) value;
            (void) timeout_millis;
            javan_socket_runtime_unsupported();
        #else
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            int timeout = javan_socket_timeout_checked(timeout_millis);
            if (socket->fd >= 0) {
                javan_socket_apply_receive_timeout(socket->fd, timeout, "server socket SO_RCVTIMEO update failed");
            }
            socket->so_timeout = timeout;
        #endif
        }

        void* javan_server_socket_get_inet_address(void* value) {
            return javan_server_socket_checked(value)->local_address;
        }

        int javan_server_socket_get_reuse_address(void* value) {
            return javan_server_socket_checked(value)->reuse_address;
        }

        void javan_server_socket_set_reuse_address(void* value, int enabled) {
        #if defined(_WIN32)
            (void) value;
            (void) enabled;
            javan_socket_runtime_unsupported();
        #else
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (socket->fd >= 0) {
                javan_socket_setsockopt_flag(socket->fd, SOL_SOCKET, SO_REUSEADDR, enabled, "server socket SO_REUSEADDR update failed");
            }
            socket->reuse_address = enabled == 0 ? 0 : 1;
        #endif
        }

        int javan_server_socket_get_receive_buffer_size(void* value) {
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return 0;
        #else
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (socket->fd < 0) {
                return socket->receive_buffer_size;
            }
            return javan_socket_getsockopt_int(socket->fd, SOL_SOCKET, SO_RCVBUF, "server socket SO_RCVBUF lookup failed");
        #endif
        }

        void javan_server_socket_set_receive_buffer_size(void* value, int size) {
        #if defined(_WIN32)
            (void) value;
            (void) size;
            javan_socket_runtime_unsupported();
        #else
            javan_server_socket* socket = javan_server_socket_checked(value);
            if (socket->closed != 0) {
                javan_panic("server socket is closed");
            }
            int checked = javan_socket_buffer_size_checked(size);
            if (socket->fd >= 0) {
                javan_socket_setsockopt_int(socket->fd, SOL_SOCKET, SO_RCVBUF, checked, "server socket SO_RCVBUF update failed");
            }
            socket->receive_buffer_size = checked;
        #endif
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
        #if defined(_WIN32)
            (void) value;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            javan_server_socket* server = javan_server_socket_checked(value);
            if (server->closed != 0) {
                javan_panic("server socket is closed");
            }
            if (server->fd < 0 || server->bound == 0) {
                javan_panic("server socket is not bound");
            }
            javan_socket_wait_readable(server->fd, server->so_timeout, "server socket accept timed out", "server socket accept wait failed");
            int accepted = accept(server->fd, NULL, NULL);
            if (accepted < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    javan_panic("server socket accept timed out");
                }
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

    private RuntimeSourcePlatformSection() {
    }

    static String tail() {
        return SOURCE_TAIL_A.concat(SOURCE_TAIL_B);
    }
}
