package javan.codegen;

final class RuntimeSourcePlatformSection {
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

    private RuntimeSourcePlatformSection() {
    }

    static String tail() {
        return SOURCE_TAIL;
    }
}
