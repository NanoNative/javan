package javan.codegen;

final class RuntimeSourceMemorySections {
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
            unsigned long long magic;
            unsigned long size;
        } javan_export_header;

        #define JAVAN_EXPORT_ALLOCATION_MAGIC 0x4a4156414e454650ULL
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
        #define JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER 22
        #define JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY 23
        #define JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR 24
        #define JAVAN_RUNTIME_KIND_CLASS 25

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
            int counter_mode;
            int closed;
            int reserved0;
            long long next_counter;
            void* fixed_name;
            void* counter_prefix;
        } javan_virtual_thread_name_state;

        typedef struct {
            int magic;
            int closed;
            int reserved0;
            int reserved1;
            void* factory;
            javan_object_list* threads;
        } javan_virtual_thread_executor_state;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            const char* binary_name;
        } javan_runtime_class_state;

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
        #define JAVAN_VIRTUAL_THREAD_BUILDER_MAGIC 0x4a565442
        #define JAVAN_VIRTUAL_THREAD_FACTORY_MAGIC 0x4a565446
        #define JAVAN_VIRTUAL_THREAD_EXECUTOR_MAGIC 0x4a565445
        #define JAVAN_RUNTIME_CLASS_MAGIC 0x4a434c53
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

        typedef struct javan_thread javan_thread;

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
        static JAVAN_THREAD_LOCAL javan_root_frame* javan_root_frames_value = NULL;
        static JAVAN_THREAD_LOCAL javan_native_resource_frame* javan_native_resource_frames_value = NULL;
        static JAVAN_THREAD_LOCAL int javan_root_frame_depth_value = 0;
        static JAVAN_THREAD_LOCAL int javan_frame_root_count_value = 0;
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
        static void** javan_thread_roots_value = NULL;
        static javan_root_frame*** javan_thread_root_frame_heads_value = NULL;
        static int javan_thread_root_count_value = 0;
        static int javan_thread_root_capacity_value = 0;
        static JAVAN_THREAD_LOCAL void* javan_current_thread_value = NULL;
        static int javan_runtime_profile_registered = 0;
        static unsigned long javan_profile_platform_thread_objects_created_value = 0;
        static unsigned long javan_profile_virtual_thread_objects_created_value = 0;
        static unsigned long javan_profile_thread_start_calls_value = 0;
        static unsigned long javan_profile_thread_completion_count_value = 0;
        static unsigned long javan_profile_thread_join_calls_value = 0;
        static unsigned long javan_profile_thread_join_interruptions_value = 0;
        static unsigned long javan_profile_thread_interrupt_calls_value = 0;
        static unsigned long javan_profile_thread_park_calls_value = 0;
        static unsigned long javan_profile_thread_park_nanos_calls_value = 0;
        static unsigned long javan_profile_thread_park_until_calls_value = 0;
        static unsigned long javan_profile_thread_unpark_calls_value = 0;
        static unsigned long javan_profile_thread_local_get_calls_value = 0;
        static unsigned long javan_profile_thread_local_set_calls_value = 0;
        static unsigned long javan_profile_thread_local_remove_calls_value = 0;
        static unsigned long javan_profile_executor_execute_calls_value = 0;
        static const char* javan_runtime_profile_json_path_value = NULL;
        static const char* javan_runtime_profile_md_path_value = NULL;
        #if defined(_WIN32)
        static CRITICAL_SECTION javan_runtime_lock_value;
        static INIT_ONCE javan_runtime_lock_once = INIT_ONCE_STATIC_INIT;
        static JAVAN_THREAD_LOCAL int javan_runtime_lock_depth_value = 0;

        static BOOL CALLBACK javan_runtime_lock_initialize_once(
            PINIT_ONCE once,
            PVOID parameter,
            PVOID* context
        ) {
            (void) once;
            (void) parameter;
            (void) context;
            InitializeCriticalSection(&javan_runtime_lock_value);
            return TRUE;
        }

        static int javan_runtime_lock_ensure_initialized(void) {
            return InitOnceExecuteOnce(
                &javan_runtime_lock_once,
                javan_runtime_lock_initialize_once,
                NULL,
                NULL
            ) != 0;
        }

        static void javan_runtime_lock_enter(void) {
            if (javan_runtime_lock_ensure_initialized() == 0) {
                javan_panic("unable to initialize runtime lock");
            }
            EnterCriticalSection(&javan_runtime_lock_value);
            javan_runtime_lock_depth_value++;
        }

        static void javan_runtime_lock_leave(void) {
            if (javan_runtime_lock_depth_value <= 0) {
                javan_panic("runtime lock underflow");
            }
            javan_runtime_lock_depth_value--;
            LeaveCriticalSection(&javan_runtime_lock_value);
        }

        static void javan_runtime_lock_reset_for_panic(void) {
            if (javan_runtime_lock_ensure_initialized() == 0) {
                return;
            }
            while (javan_runtime_lock_depth_value > 0) {
                javan_runtime_lock_depth_value--;
                LeaveCriticalSection(&javan_runtime_lock_value);
            }
        }
        #else
        static pthread_mutex_t javan_runtime_lock_value;
        static pthread_once_t javan_runtime_lock_once = PTHREAD_ONCE_INIT;
        static JAVAN_THREAD_LOCAL int javan_runtime_lock_depth_value = 0;

        static void javan_runtime_lock_initialize(void) {
            pthread_mutexattr_t attributes;
            if (pthread_mutexattr_init(&attributes) != 0) {
                javan_panic("unable to initialize runtime lock");
            }
            if (pthread_mutexattr_settype(&attributes, PTHREAD_MUTEX_RECURSIVE) != 0) {
                pthread_mutexattr_destroy(&attributes);
                javan_panic("unable to initialize runtime lock");
            }
            if (pthread_mutex_init(&javan_runtime_lock_value, &attributes) != 0) {
                pthread_mutexattr_destroy(&attributes);
                javan_panic("unable to initialize runtime lock");
            }
            pthread_mutexattr_destroy(&attributes);
        }

        static void javan_runtime_lock_enter(void) {
            if (pthread_once(&javan_runtime_lock_once, javan_runtime_lock_initialize) != 0) {
                javan_panic("unable to initialize runtime lock");
            }
            if (pthread_mutex_lock(&javan_runtime_lock_value) != 0) {
                javan_panic("unable to acquire runtime lock");
            }
            javan_runtime_lock_depth_value++;
        }

        static void javan_runtime_lock_leave(void) {
            if (javan_runtime_lock_depth_value <= 0) {
                javan_panic("runtime lock underflow");
            }
            javan_runtime_lock_depth_value--;
            if (pthread_mutex_unlock(&javan_runtime_lock_value) != 0) {
                javan_panic("unable to release runtime lock");
            }
        }

        static void javan_runtime_lock_reset_for_panic(void) {
            if (pthread_once(&javan_runtime_lock_once, javan_runtime_lock_initialize) != 0) {
                return;
            }
            while (javan_runtime_lock_depth_value > 0) {
                javan_runtime_lock_depth_value--;
                (void) pthread_mutex_unlock(&javan_runtime_lock_value);
            }
        }
        #endif

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

        static void javan_thread_root_cleanup(void) {
            free(javan_thread_roots_value);
            javan_thread_roots_value = NULL;
            free(javan_thread_root_frame_heads_value);
            javan_thread_root_frame_heads_value = NULL;
            javan_thread_root_count_value = 0;
            javan_thread_root_capacity_value = 0;
        }

        static void javan_allocator_cleanup(void) {
            javan_allocator_cleaning = 1;
            javan_native_resource_cleanup_all();
            javan_root_frame_cleanup();
            javan_thread_root_cleanup();
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

        static int javan_runtime_profile_requested(void) {
            const char* json_path = javan_runtime_profile_json_path_value;
            const char* markdown_path = javan_runtime_profile_md_path_value;
            return (json_path != NULL && json_path[0] != '\\0')
                || (markdown_path != NULL && markdown_path[0] != '\\0');
        }

        void javan_runtime_profile_consume_args(int* argc, char*** argv) {
            if (argc == NULL || argv == NULL || *argv == NULL) {
                return;
            }
            char** values = *argv;
            int input_count = *argc;
            int out = input_count > 0 ? 1 : 0;
            for (int index = 1; index < input_count; index++) {
                const char* argument = values[index];
                if (argument != NULL && strncmp(argument, "--javan-runtime-profile-json=", 29) == 0) {
                    javan_runtime_profile_json_path_value = argument + 29;
                    continue;
                }
                if (argument != NULL && strncmp(argument, "--javan-runtime-profile-md=", 27) == 0) {
                    javan_runtime_profile_md_path_value = argument + 27;
                    continue;
                }
                values[out] = values[index];
                out++;
            }
            if (out < input_count) {
                values[out] = NULL;
            }
            *argc = out;
        }

        static void javan_runtime_profile_write(void) {
            const char* json_path = javan_runtime_profile_json_path_value;
            const char* markdown_path = javan_runtime_profile_md_path_value;
            if ((json_path == NULL || json_path[0] == '\\0')
                && (markdown_path == NULL || markdown_path[0] == '\\0')) {
                return;
            }
            javan_runtime_lock_enter();
            unsigned long platform_thread_objects_created = javan_profile_platform_thread_objects_created_value;
            unsigned long virtual_thread_objects_created = javan_profile_virtual_thread_objects_created_value;
            unsigned long thread_start_calls = javan_profile_thread_start_calls_value;
            unsigned long thread_completion_count = javan_profile_thread_completion_count_value;
            unsigned long thread_join_calls = javan_profile_thread_join_calls_value;
            unsigned long thread_join_interruptions = javan_profile_thread_join_interruptions_value;
            unsigned long thread_interrupt_calls = javan_profile_thread_interrupt_calls_value;
            unsigned long thread_park_calls = javan_profile_thread_park_calls_value;
            unsigned long thread_park_nanos_calls = javan_profile_thread_park_nanos_calls_value;
            unsigned long thread_park_until_calls = javan_profile_thread_park_until_calls_value;
            unsigned long thread_unpark_calls = javan_profile_thread_unpark_calls_value;
            unsigned long thread_local_get_calls = javan_profile_thread_local_get_calls_value;
            unsigned long thread_local_set_calls = javan_profile_thread_local_set_calls_value;
            unsigned long thread_local_remove_calls = javan_profile_thread_local_remove_calls_value;
            unsigned long executor_execute_calls = javan_profile_executor_execute_calls_value;
            unsigned long registered_thread_roots = (unsigned long) javan_thread_root_count_value;
            int current_thread_root_present = 0;
            if (javan_current_thread_value != NULL) {
                for (int index = 0; index < javan_thread_root_count_value; index++) {
                    if (javan_thread_roots_value[index] == javan_current_thread_value) {
                        current_thread_root_present = 1;
                        break;
                    }
                }
            }
            unsigned long active_worker_thread_roots = registered_thread_roots;
            if (current_thread_root_present != 0 && active_worker_thread_roots > 0) {
                active_worker_thread_roots--;
            }
            javan_runtime_lock_leave();
            if (json_path != NULL && json_path[0] != '\\0') {
                FILE* json = fopen(json_path, "w");
                if (json != NULL) {
                    fprintf(json, "{\\n");
                    fprintf(json, "  \\"schemaVersion\\": 1,\\n");
                    fprintf(json, "  \\"status\\": \\"collected\\",\\n");
                    fprintf(json, "  \\"requested\\": true,\\n");
                    fprintf(json, "  \\"enabled\\": true,\\n");
                    fprintf(json, "  \\"collectionState\\": \\"collected\\",\\n");
                    fprintf(json, "  \\"reason\\": \\"Runtime profiling counters were collected during native execution.\\",\\n");
                    fprintf(json, "  \\"disabledProfilingModules\\": [],\\n");
                    fprintf(json, "  \\"platformThreadObjectsCreated\\": %lu,\\n", platform_thread_objects_created);
                    fprintf(json, "  \\"virtualThreadObjectsCreated\\": %lu,\\n", virtual_thread_objects_created);
                    fprintf(json, "  \\"threadStartCalls\\": %lu,\\n", thread_start_calls);
                    fprintf(json, "  \\"threadCompletions\\": %lu,\\n", thread_completion_count);
                    fprintf(json, "  \\"threadJoinCalls\\": %lu,\\n", thread_join_calls);
                    fprintf(json, "  \\"threadJoinInterruptions\\": %lu,\\n", thread_join_interruptions);
                    fprintf(json, "  \\"threadInterruptCalls\\": %lu,\\n", thread_interrupt_calls);
                    fprintf(json, "  \\"threadParkCalls\\": %lu,\\n", thread_park_calls);
                    fprintf(json, "  \\"threadParkNanosCalls\\": %lu,\\n", thread_park_nanos_calls);
                    fprintf(json, "  \\"threadParkUntilCalls\\": %lu,\\n", thread_park_until_calls);
                    fprintf(json, "  \\"threadUnparkCalls\\": %lu,\\n", thread_unpark_calls);
                    fprintf(json, "  \\"threadLocalGetCalls\\": %lu,\\n", thread_local_get_calls);
                    fprintf(json, "  \\"threadLocalSetCalls\\": %lu,\\n", thread_local_set_calls);
                    fprintf(json, "  \\"threadLocalRemoveCalls\\": %lu,\\n", thread_local_remove_calls);
                    fprintf(json, "  \\"executorExecuteCalls\\": %lu,\\n", executor_execute_calls);
                    fprintf(json, "  \\"registeredThreadRoots\\": %lu,\\n", registered_thread_roots);
                    fprintf(json, "  \\"activeWorkerThreadRoots\\": %lu,\\n", active_worker_thread_roots);
                    fprintf(json, "  \\"currentThreadRootPresent\\": %s\\n", current_thread_root_present != 0 ? "true" : "false");
                    fprintf(json, "}\\n");
                    fclose(json);
                }
            }
            if (markdown_path != NULL && markdown_path[0] != '\\0') {
                FILE* markdown = fopen(markdown_path, "w");
                if (markdown != NULL) {
                    fprintf(markdown, "# Runtime Profiling\\n\\n");
                    fprintf(markdown, "- status: `collected`\\n");
                    fprintf(markdown, "- requested: `true`\\n");
                    fprintf(markdown, "- enabled: `true`\\n");
                    fprintf(markdown, "- collectionState: `collected`\\n");
                    fprintf(markdown, "- disabledProfilingModules: `-`\\n");
                    fprintf(markdown, "- reason: Runtime profiling counters were collected during native execution.\\n");
                    fprintf(markdown, "- platformThreadObjectsCreated: `%lu`\\n", platform_thread_objects_created);
                    fprintf(markdown, "- virtualThreadObjectsCreated: `%lu`\\n", virtual_thread_objects_created);
                    fprintf(markdown, "- threadStartCalls: `%lu`\\n", thread_start_calls);
                    fprintf(markdown, "- threadCompletions: `%lu`\\n", thread_completion_count);
                    fprintf(markdown, "- threadJoinCalls: `%lu`\\n", thread_join_calls);
                    fprintf(markdown, "- threadJoinInterruptions: `%lu`\\n", thread_join_interruptions);
                    fprintf(markdown, "- threadInterruptCalls: `%lu`\\n", thread_interrupt_calls);
                    fprintf(markdown, "- threadParkCalls: `%lu`\\n", thread_park_calls);
                    fprintf(markdown, "- threadParkNanosCalls: `%lu`\\n", thread_park_nanos_calls);
                    fprintf(markdown, "- threadParkUntilCalls: `%lu`\\n", thread_park_until_calls);
                    fprintf(markdown, "- threadUnparkCalls: `%lu`\\n", thread_unpark_calls);
                    fprintf(markdown, "- threadLocalGetCalls: `%lu`\\n", thread_local_get_calls);
                    fprintf(markdown, "- threadLocalSetCalls: `%lu`\\n", thread_local_set_calls);
                    fprintf(markdown, "- threadLocalRemoveCalls: `%lu`\\n", thread_local_remove_calls);
                    fprintf(markdown, "- executorExecuteCalls: `%lu`\\n", executor_execute_calls);
                    fprintf(markdown, "- registeredThreadRoots: `%lu`\\n", registered_thread_roots);
                    fprintf(markdown, "- activeWorkerThreadRoots: `%lu`\\n", active_worker_thread_roots);
                    fprintf(markdown, "- currentThreadRootPresent: `%s`\\n", current_thread_root_present != 0 ? "true" : "false");
                    fclose(markdown);
                }
            }
        }

        static void javan_allocator_ensure_cleanup(void) {
            if (javan_allocator_cleanup_registered == 0) {
                if (atexit(javan_allocator_cleanup) != 0) {
                    javan_panic("unable to register allocator cleanup");
                }
                javan_allocator_cleanup_registered = 1;
            }
            if (javan_runtime_profile_registered == 0 && javan_runtime_profile_requested() != 0) {
                if (atexit(javan_runtime_profile_write) != 0) {
                    javan_panic("unable to register runtime profiling cleanup");
                }
                javan_runtime_profile_registered = 1;
            }
        }

        static void javan_track_allocation(void* value, void* base, unsigned long size, int kind, int type_id) {
            javan_runtime_lock_enter();
            javan_allocator_ensure_cleanup();
            javan_allocation_node* node = (javan_allocation_node*) malloc(sizeof(javan_allocation_node));
            if (node == NULL) {
                javan_gc_collect();
                node = (javan_allocation_node*) malloc(sizeof(javan_allocation_node));
                if (node == NULL) {
                    javan_runtime_lock_leave();
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
            javan_runtime_lock_leave();
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
            javan_runtime_lock_enter();
            if (count < 0) {
                javan_runtime_lock_leave();
                javan_panic("invalid type descriptor count");
            }
            if (count > 0 && descriptors == NULL) {
                javan_runtime_lock_leave();
                javan_panic("invalid type descriptor inventory");
            }
            for (int index = 0; index < count; index++) {
                if (descriptors[index].type_id == 0 || descriptors[index].name == NULL || descriptors[index].object_field_count < 0) {
                    javan_runtime_lock_leave();
                    javan_panic("invalid type descriptor");
                }
                if (descriptors[index].object_field_count > 0 && descriptors[index].object_field_offsets == NULL) {
                    javan_runtime_lock_leave();
                    javan_panic("invalid type field descriptor");
                }
            }
            javan_type_descriptors_value = descriptors;
            javan_type_descriptor_count_value = count;
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        #define JAVAN_TYPE_JAVA_LANG_INTEGER -1001
        #define JAVAN_TYPE_JAVA_LANG_LONG -1002
        #define JAVAN_TYPE_JAVA_LANG_FLOAT -1003
        #define JAVAN_TYPE_JAVA_LANG_DOUBLE -1004
        #define JAVAN_TYPE_JAVA_LANG_BOOLEAN -1005
        #define JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME -1006
        #define JAVAN_TYPE_JAVA_TIME_DURATION -1007
        #define JAVAN_TYPE_JAVA_LANG_THREAD -1008
        #define JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL -1009

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
                || type_id == JAVAN_TYPE_JAVA_LANG_THREAD
                || type_id == JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL;
        }

        static void javan_update_allocation_metadata(void* value, int kind, int type_id) {
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_runtime_lock_leave();
                javan_panic("unknown runtime allocation");
            }
            node->kind = kind;
            node->type_id = type_id;
            node->collectible = ((kind == JAVAN_HEAP_KIND_OBJECT && javan_object_kind_collectible(type_id) != 0)
                || (kind == JAVAN_HEAP_KIND_ARRAY && javan_array_kind_collectible(type_id) != 0)) ? 1 : 0;
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        static void javan_update_runtime_allocation_kind(void* value, int runtime_kind) {
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_runtime_lock_leave();
                javan_panic("unknown runtime allocation");
            }
            if (node->kind != JAVAN_HEAP_KIND_RUNTIME) {
                javan_runtime_lock_leave();
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
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR
                || runtime_kind == JAVAN_RUNTIME_KIND_CLASS
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
            javan_runtime_lock_leave();
        }

        void javan_root_frame_push(void*** roots, int count) {
            javan_runtime_lock_enter();
            if (count < 0) {
                javan_runtime_lock_leave();
                javan_panic("invalid root frame count");
            }
            if (count > 0 && roots == NULL) {
                javan_runtime_lock_leave();
                javan_panic("invalid root frame");
            }
            for (int index = 0; index < count; index++) {
                if (roots[index] == NULL) {
                    javan_runtime_lock_leave();
                    javan_panic("invalid root frame slot");
                }
            }
            javan_allocator_ensure_cleanup();
            javan_root_frame* frame = (javan_root_frame*) malloc(sizeof(javan_root_frame));
            if (frame == NULL) {
                javan_runtime_lock_leave();
                javan_panic("out of memory");
            }
            frame->roots = roots;
            frame->count = count;
            frame->next = javan_root_frames_value;
            javan_root_frames_value = frame;
            javan_root_frame_depth_value++;
            javan_frame_root_count_value += count;
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        void javan_root_frame_pop(void*** roots) {
            javan_runtime_lock_enter();
            javan_root_frame* frame = javan_root_frames_value;
            if (frame == NULL) {
                javan_runtime_lock_leave();
                javan_panic("root frame underflow");
            }
            if (frame->roots != roots) {
                javan_runtime_lock_leave();
                javan_panic("root frame pop mismatch");
            }
            javan_root_frames_value = frame->next;
            javan_root_frame_depth_value--;
            javan_frame_root_count_value -= frame->count;
            free(frame);
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        void javan_register_static_roots(void*** roots, int count) {
            javan_runtime_lock_enter();
            if (count < 0) {
                javan_runtime_lock_leave();
                javan_panic("invalid static root count");
            }
            if (count > 0 && roots == NULL) {
                javan_runtime_lock_leave();
                javan_panic("invalid static root inventory");
            }
            javan_static_roots_value = roots;
            javan_static_root_count_value = count;
            javan_gc_enabled_value = 1;
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                || node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY) {
                javan_virtual_thread_name_state* state = (javan_virtual_thread_name_state*) node->value;
                int expected_magic = node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                    ? JAVAN_VIRTUAL_THREAD_BUILDER_MAGIC
                    : JAVAN_VIRTUAL_THREAD_FACTORY_MAGIC;
                if (state->magic != expected_magic
                    || (state->counter_mode != 0 && state->counter_mode != 1)
                    || (state->closed != 0 && state->closed != 1)) {
                    javan_panic("invalid runtime virtual thread naming metadata");
                }
                if (state->counter_mode != 0) {
                    javan_validate_runtime_managed_reference(state->counter_prefix);
                } else {
                    javan_validate_runtime_managed_reference(state->fixed_name);
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR) {
                javan_virtual_thread_executor_state* state = (javan_virtual_thread_executor_state*) node->value;
                if (state->magic != JAVAN_VIRTUAL_THREAD_EXECUTOR_MAGIC
                    || (state->closed != 0 && state->closed != 1)
                    || state->threads == NULL) {
                    javan_panic("invalid runtime virtual thread executor metadata");
                }
                javan_validate_runtime_managed_reference(state->factory);
                javan_validate_runtime_managed_reference((void*) state->threads);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS) {
                javan_runtime_class_state* state = (javan_runtime_class_state*) node->value;
                if (state->magic != JAVAN_RUNTIME_CLASS_MAGIC || state->binary_name == NULL || state->binary_name[0] == '\\0') {
                    javan_panic("invalid runtime class metadata");
                }
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
            javan_runtime_lock_enter();
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
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_RESPONSE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_CLASS) {
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
            if (javan_thread_root_count_value < 0) {
                javan_panic("invalid thread root count");
            }
            if (javan_thread_root_capacity_value < javan_thread_root_count_value) {
                javan_panic("invalid thread root capacity");
            }
            if (javan_thread_root_capacity_value > 0
                && (javan_thread_roots_value == NULL || javan_thread_root_frame_heads_value == NULL)) {
                javan_panic("invalid thread root inventory");
            }
            for (int index = 0; index < javan_thread_root_count_value; index++) {
                if (javan_thread_roots_value[index] == NULL) {
                    javan_panic("invalid thread root slot");
                }
                for (int next = index + 1; next < javan_thread_root_count_value; next++) {
                    if (javan_thread_roots_value[index] == javan_thread_roots_value[next]) {
                        javan_panic("duplicate thread root");
                    }
                }
            }
            javan_runtime_lock_leave();
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
    private static final String SOURCE_HEAP_ALLOC_HEAD = """
        void* javan_alloc(unsigned long size) {
            javan_runtime_lock_enter();
            unsigned long actual_size = size == 0 ? 1 : size;
            javan_prepare_allocation(actual_size);
            void* value = javan_calloc_checked(actual_size);
            if (javan_allocator_cleaning == 0) {
                javan_track_allocation(value, value, actual_size, JAVAN_HEAP_KIND_RUNTIME, 0);
            }
            javan_runtime_lock_leave();
            return value;
        }

        static char* javan_string_alloc(unsigned long size) {
            char* value = (char*) javan_alloc(size);
            javan_update_runtime_allocation_kind((void*) value, JAVAN_RUNTIME_KIND_STRING);
            return value;
        }

        static void* javan_export_alloc(unsigned long size) {
            javan_runtime_lock_enter();
            unsigned long actual_size = size == 0 ? 1 : size;
            javan_prepare_allocation(actual_size);
            if (actual_size > ULONG_MAX - sizeof(javan_export_header)) {
                javan_runtime_lock_leave();
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
            javan_runtime_lock_leave();
            return value;
        }

        static void* javan_realloc_tracked(void* value, unsigned long size, int validate_after) {
            javan_runtime_lock_enter();
            if (value == NULL) {
                javan_runtime_lock_leave();
                return javan_alloc(size);
            }
            unsigned long actual_size = size == 0 ? 1 : size;
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_runtime_lock_leave();
                javan_panic("unknown runtime allocation");
            }
            if (node->base != value) {
                javan_runtime_lock_leave();
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
            javan_runtime_lock_leave();
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
            javan_runtime_lock_enter();
            if (value == NULL) {
                javan_runtime_lock_leave();
                return;
            }
            javan_allocation_node* previous = NULL;
            javan_allocation_node* node = javan_find_allocation(value, &previous);
            if (node == NULL) {
                javan_runtime_lock_leave();
                return;
            }
            if (node->kind != JAVAN_HEAP_KIND_RUNTIME || node->runtime_kind != JAVAN_RUNTIME_KIND_OWNED_BUFFER) {
                javan_runtime_lock_leave();
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
            javan_runtime_lock_leave();
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

        static void javan_release_thread_native_state(javan_thread* thread);
        static javan_object_map* javan_map_checked(void* value);
        static void javan_map_ensure_capacity(javan_object_map* map, int required);
        void* javan_hashmap_new(void);
        void* javan_map_remove(void* value, void* key);
        static javan_thread* javan_current_thread_object(void);

        void javan_free(void* value) {
            javan_runtime_lock_enter();
            if (value == NULL) {
                javan_runtime_lock_leave();
                return;
            }
            javan_allocation_node* previous = NULL;
            javan_allocation_node* node = javan_find_allocation(value, &previous);
            if (node == NULL) {
                javan_runtime_lock_leave();
                javan_panic("unknown runtime allocation");
            }
            javan_release_runtime_owned_buffers(node);
            previous = NULL;
            node = javan_find_allocation(value, &previous);
            if (node == NULL) {
                javan_runtime_lock_leave();
                return;
            }
            if (previous == NULL) {
                javan_allocations = node->next;
            } else {
                previous->next = node->next;
            }
            if (node->kind == JAVAN_HEAP_KIND_OBJECT) {
                if (node->type_id == JAVAN_TYPE_JAVA_LANG_THREAD) {
                    javan_release_thread_native_state((javan_thread*) node->value);
                }
                javan_object_registry_remove(value);
            }
            void* base = node->base;
            javan_account_free(node->size);
            free(node);
            free(base);
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
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
            javan_runtime_lock_enter();
            if (value == NULL || type_id == 0) {
                javan_runtime_lock_leave();
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
            javan_runtime_lock_leave();
        }

        static int javan_registered_type_id(void* value) {
            javan_runtime_lock_enter();
            if (value == NULL) {
                javan_runtime_lock_leave();
                return 0;
            }
            if (javan_objects.capacity <= 0) {
                javan_runtime_lock_leave();
                return 0;
            }
            int index = javan_registry_slot(javan_objects.values, javan_objects.capacity, value);
            int result = javan_objects.values[index] == value ? javan_objects.type_ids[index] : 0;
            javan_runtime_lock_leave();
            return result;
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
            int reserved;
        } javan_thread_local;

        typedef struct javan_thread {
            int interrupted;
            int started;
            int completed;
            int virtual_thread;
            int park_permit;
            char* name;
            #if defined(_WIN32)
            void* native_handle;
            CONDITION_VARIABLE native_completion_cond;
            SRWLOCK native_completion_lock;
            int native_completion_signaled;
            #else
            pthread_mutex_t native_completion_mutex;
            pthread_cond_t native_completion_cond;
            int native_completion_signaled;
            int native_sync_initialized;
            #endif
            void* target;
            void* thread_locals;
        } javan_thread;

        static long long javan_platform_thread_name_counter_value = 0;

        static char* javan_thread_copy_default_platform_name(void) {
            char buffer[32];
            long long next = 0;
            javan_runtime_lock_enter();
            next = javan_platform_thread_name_counter_value;
            javan_platform_thread_name_counter_value++;
            javan_runtime_lock_leave();
            snprintf(buffer, sizeof(buffer), "Thread-%lld", next);
            buffer[sizeof(buffer) - 1] = '\\0';
            return (char*) javan_string_copy(buffer);
        }

        static char* javan_thread_copy_default_virtual_name(void) {
            return (char*) javan_string_copy("");
        }

        static void javan_thread_assign_name_text(javan_thread* thread, const char* value) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            thread->name = value == NULL
                ? javan_thread_copy_default_virtual_name()
                : (char*) javan_string_copy(value);
        }

        static javan_virtual_thread_name_state* javan_virtual_thread_name_state_checked(
            void* value,
            int expected_magic,
            const char* kind
        ) {
            if (value == NULL) {
                javan_panic(kind);
            }
            javan_virtual_thread_name_state* state = (javan_virtual_thread_name_state*) value;
            if (state->magic != expected_magic || (state->counter_mode != 0 && state->counter_mode != 1)) {
                javan_panic(kind);
            }
            return state;
        }

        static javan_virtual_thread_name_state* javan_virtual_thread_builder_checked(void* value) {
            return javan_virtual_thread_name_state_checked(value, JAVAN_VIRTUAL_THREAD_BUILDER_MAGIC, "unsupported virtual thread builder");
        }

        static javan_virtual_thread_name_state* javan_virtual_thread_factory_checked(void* value) {
            return javan_virtual_thread_name_state_checked(value, JAVAN_VIRTUAL_THREAD_FACTORY_MAGIC, "unsupported virtual thread factory");
        }

        static javan_virtual_thread_executor_state* javan_virtual_thread_executor_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported virtual thread executor");
            }
            javan_virtual_thread_executor_state* state = (javan_virtual_thread_executor_state*) value;
            if (state->magic != JAVAN_VIRTUAL_THREAD_EXECUTOR_MAGIC || state->threads == NULL) {
                javan_panic("unsupported virtual thread executor");
            }
            return state;
        }

        static javan_runtime_class_state* javan_runtime_class_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported runtime class");
            }
            javan_runtime_class_state* state = (javan_runtime_class_state*) value;
            if (state->magic != JAVAN_RUNTIME_CLASS_MAGIC || state->binary_name == NULL || state->binary_name[0] == '\\0') {
                javan_panic("unsupported runtime class");
            }
            return state;
        }

        static void* javan_virtual_thread_name_state_new(int runtime_kind, int magic) {
            javan_virtual_thread_name_state* state = (javan_virtual_thread_name_state*) javan_alloc(sizeof(javan_virtual_thread_name_state));
            state->magic = magic;
            state->counter_mode = 0;
            state->closed = 0;
            state->reserved0 = 0;
            state->next_counter = 0;
            state->fixed_name = NULL;
            state->counter_prefix = NULL;
            javan_update_runtime_allocation_kind((void*) state, runtime_kind);
            return state;
        }

        static void* javan_runtime_class_new(const char* binary_name) {
            if (binary_name == NULL || binary_name[0] == '\\0') {
                javan_panic("invalid runtime class name");
            }
            javan_runtime_class_state* state = (javan_runtime_class_state*) javan_alloc(sizeof(javan_runtime_class_state));
            state->magic = JAVAN_RUNTIME_CLASS_MAGIC;
            state->reserved0 = 0;
            state->reserved1 = 0;
            state->reserved2 = 0;
            state->binary_name = binary_name;
            javan_update_runtime_allocation_kind((void*) state, JAVAN_RUNTIME_KIND_CLASS);
            return state;
        }

        void* javan_virtual_thread_builder_new(void) {
            return javan_virtual_thread_name_state_new(
                JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER,
                JAVAN_VIRTUAL_THREAD_BUILDER_MAGIC
            );
        }

        void* javan_virtual_thread_builder_name(void* value, void* name) {
            javan_virtual_thread_name_state* state = javan_virtual_thread_builder_checked(value);
            state->counter_mode = 0;
            state->fixed_name = name;
            state->counter_prefix = NULL;
            return value;
        }

        void* javan_virtual_thread_builder_name_counter(void* value, void* prefix, long long start) {
            javan_virtual_thread_name_state* state = javan_virtual_thread_builder_checked(value);
            state->counter_mode = 1;
            state->next_counter = start;
            state->fixed_name = NULL;
            state->counter_prefix = prefix;
            return value;
        }

        static void* javan_virtual_thread_name_state_next_name(javan_virtual_thread_name_state* state) {
            if (state == NULL) {
                javan_panic("invalid virtual thread naming state");
            }
            if (state->counter_mode == 0) {
                return state->fixed_name;
            }
            void* state_root = (void*) state;
            void* prefix_value = state->counter_prefix;
            void* builder_value = NULL;
            void* result = NULL;
            void** roots[] = {
                (void**) &state_root,
                (void**) &prefix_value,
                (void**) &builder_value,
                (void**) &result
            };
            javan_root_frame_push(roots, 4);
            builder_value = javan_stringbuilder_new();
            builder_value = javan_stringbuilder_append_string(builder_value, prefix_value);
            builder_value = javan_stringbuilder_append_long(builder_value, state->next_counter);
            result = javan_stringbuilder_to_string(builder_value);
            ((javan_virtual_thread_name_state*) state_root)->next_counter++;
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_virtual_thread_builder_factory(void* value) {
            void* builder_root = value;
            void* factory_value = NULL;
            void** roots[] = {
                (void**) &builder_root,
                (void**) &factory_value
            };
            javan_root_frame_push(roots, 2);
            javan_virtual_thread_name_state* builder = javan_virtual_thread_builder_checked(builder_root);
            factory_value = javan_virtual_thread_name_state_new(
                JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY,
                JAVAN_VIRTUAL_THREAD_FACTORY_MAGIC
            );
            javan_virtual_thread_name_state* factory = javan_virtual_thread_factory_checked(factory_value);
            factory->counter_mode = builder->counter_mode;
            factory->next_counter = builder->next_counter;
            factory->fixed_name = builder->fixed_name;
            factory->counter_prefix = builder->counter_prefix;
            javan_root_frame_pop(roots);
            return factory_value;
        }

        static void* javan_virtual_thread_name_state_thread(
            javan_virtual_thread_name_state* state,
            void* runnable,
            int start
        ) {
            void* state_root = (void*) state;
            void* runnable_root = runnable;
            void* name_value = NULL;
            void* thread_value = NULL;
            void** roots[] = {
                (void**) &state_root,
                (void**) &runnable_root,
                (void**) &name_value,
                (void**) &thread_value
            };
            javan_root_frame_push(roots, 4);
            name_value = javan_virtual_thread_name_state_next_name((javan_virtual_thread_name_state*) state_root);
            thread_value = javan_thread_new_virtual();
            if (name_value != NULL) {
                javan_thread_set_name(thread_value, name_value);
            }
            javan_thread_set_target(thread_value, runnable_root);
            if (start != 0) {
                javan_thread_start(thread_value);
            }
            javan_root_frame_pop(roots);
            return thread_value;
        }

        void* javan_virtual_thread_builder_start(void* value, void* runnable) {
            return javan_virtual_thread_name_state_thread(
                javan_virtual_thread_builder_checked(value),
                runnable,
                1
            );
        }

        void* javan_virtual_thread_builder_unstarted(void* value, void* runnable) {
            return javan_virtual_thread_name_state_thread(
                javan_virtual_thread_builder_checked(value),
                runnable,
                0
            );
        }

        void* javan_virtual_thread_factory_new_thread(void* value, void* runnable) {
            return javan_virtual_thread_name_state_thread(
                javan_virtual_thread_factory_checked(value),
                runnable,
                0
            );
        }

        void* javan_virtual_thread_builder_get_class(void* value) {
            javan_virtual_thread_builder_checked(value);
            return javan_runtime_class_new("java.lang.ThreadBuilders$VirtualThreadBuilder");
        }

        void* javan_virtual_thread_factory_get_class(void* value) {
            javan_virtual_thread_factory_checked(value);
            return javan_runtime_class_new("java.lang.ThreadBuilders$VirtualThreadFactory");
        }

        void* javan_virtual_thread_executor_get_class(void* value) {
            javan_virtual_thread_executor_checked(value);
            return javan_runtime_class_new("java.util.concurrent.ThreadPerTaskExecutor");
        }

        void* javan_runtime_class_get_name(void* value) {
            return javan_string_from(javan_runtime_class_checked(value)->binary_name);
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_EXECUTOR = """
        static javan_object_list* javan_list_new_with_capacity(int capacity, int immutable);
        static void javan_list_append_raw(javan_object_list* list, void* value);
        void* javan_virtual_thread_executor_from_factory(void* value);

        void* javan_virtual_thread_executor_new(void) {
            void* factory_value = NULL;
            void* executor_value = NULL;
            void** roots[] = {
                (void**) &factory_value,
                (void**) &executor_value
            };
            javan_root_frame_push(roots, 2);
            factory_value = javan_virtual_thread_name_state_new(
                JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY,
                JAVAN_VIRTUAL_THREAD_FACTORY_MAGIC
            );
            executor_value = javan_virtual_thread_executor_from_factory(factory_value);
            javan_root_frame_pop(roots);
            return executor_value;
        }

        void* javan_virtual_thread_executor_from_factory(void* value) {
            void* factory_root = value;
            void* list_value = NULL;
            void* executor_value = NULL;
            void** roots[] = {
                (void**) &factory_root,
                (void**) &list_value,
                (void**) &executor_value
            };
            javan_root_frame_push(roots, 3);
            javan_virtual_thread_factory_checked(factory_root);
            list_value = javan_list_new_with_capacity(0, 0);
            executor_value = javan_alloc(sizeof(javan_virtual_thread_executor_state));
            javan_virtual_thread_executor_state* state = (javan_virtual_thread_executor_state*) executor_value;
            state->magic = JAVAN_VIRTUAL_THREAD_EXECUTOR_MAGIC;
            state->closed = 0;
            state->reserved0 = 0;
            state->reserved1 = 0;
            state->factory = factory_root;
            state->threads = (javan_object_list*) list_value;
            javan_update_runtime_allocation_kind(executor_value, JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR);
            javan_root_frame_pop(roots);
            return executor_value;
        }

        void javan_virtual_thread_executor_execute(void* value, void* runnable) {
            void* executor_root = value;
            void* runnable_root = runnable;
            void* thread_value = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &runnable_root,
                (void**) &thread_value
            };
            javan_root_frame_push(roots, 3);
            javan_virtual_thread_executor_state* state = javan_virtual_thread_executor_checked(executor_root);
            if (state->closed != 0) {
                javan_panic("virtual thread executor is closed");
            }
            javan_profile_executor_execute_calls_value++;
            thread_value = javan_virtual_thread_factory_new_thread(state->factory, runnable_root);
            javan_thread_start(thread_value);
            javan_list_append_raw(state->threads, thread_value);
            javan_root_frame_pop(roots);
        }

        void javan_virtual_thread_executor_shutdown(void* value) {
            javan_virtual_thread_executor_checked(value)->closed = 1;
        }

        void javan_virtual_thread_executor_close(void* value) {
            javan_virtual_thread_executor_state* state = javan_virtual_thread_executor_checked(value);
            state->closed = 1;
            if (state->threads == NULL || state->threads->length <= 0 || state->threads->values == NULL) {
                return;
            }
            for (int index = 0; index < state->threads->length; index++) {
                void* thread_value = state->threads->values[index];
                if (thread_value != NULL) {
                    javan_thread_join(thread_value);
                }
            }
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_TAIL = """
        void* javan_thread_new(void) {
            javan_thread* object = (javan_thread*) javan_alloc(sizeof(javan_thread));
            object->interrupted = 0;
            object->started = 0;
            object->completed = 0;
            object->virtual_thread = 0;
            object->park_permit = 0;
            object->name = NULL;
            #if defined(_WIN32)
            object->native_handle = NULL;
            InitializeConditionVariable(&object->native_completion_cond);
            InitializeSRWLock(&object->native_completion_lock);
            object->native_completion_signaled = 0;
            #else
            if (pthread_mutex_init(&object->native_completion_mutex, NULL) != 0) {
                javan_panic("unable to initialize thread completion mutex");
            }
            if (pthread_cond_init(&object->native_completion_cond, NULL) != 0) {
                pthread_mutex_destroy(&object->native_completion_mutex);
                javan_panic("unable to initialize thread completion condition");
            }
            object->native_completion_signaled = 0;
            object->native_sync_initialized = 1;
            #endif
            object->target = NULL;
            object->thread_locals = NULL;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_THREAD);
            void* rooted_object = (void*) object;
            void** javan_thread_new_roots[] = { &rooted_object };
            javan_root_frame_push(javan_thread_new_roots, 1);
            object->name = javan_thread_copy_default_platform_name();
            javan_root_frame_pop(javan_thread_new_roots);
            javan_runtime_lock_enter();
            javan_profile_platform_thread_objects_created_value++;
            javan_runtime_lock_leave();
            return object;
        }

        void* javan_thread_new_virtual(void) {
            void* value = javan_thread_new();
            javan_thread* object = (javan_thread*) value;
            object->virtual_thread = 1;
            void** javan_thread_new_virtual_roots[] = { &value };
            javan_root_frame_push(javan_thread_new_virtual_roots, 1);
            object->name = javan_thread_copy_default_virtual_name();
            javan_root_frame_pop(javan_thread_new_virtual_roots);
            javan_runtime_lock_enter();
            if (javan_profile_platform_thread_objects_created_value > 0) {
                javan_profile_platform_thread_objects_created_value--;
            }
            javan_profile_virtual_thread_objects_created_value++;
            javan_runtime_lock_leave();
            return value;
        }

        void* javan_thread_local_new(void) {
            javan_thread_local* object = (javan_thread_local*) javan_alloc(sizeof(javan_thread_local));
            object->reserved = 0;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL);
            return object;
        }

        void* javan_integer_value_of(int value) {
            javan_boxed_int* object = (javan_boxed_int*) javan_alloc(sizeof(javan_boxed_int));
            object->value = value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_INTEGER);
            return object;
        }

        static void javan_release_thread_native_state(javan_thread* thread) {
            if (thread == NULL) {
                return;
            }
            #if defined(_WIN32)
            thread->native_handle = NULL;
            #else
            if (thread->native_sync_initialized != 0) {
                pthread_cond_destroy(&thread->native_completion_cond);
                pthread_mutex_destroy(&thread->native_completion_mutex);
                thread->native_sync_initialized = 0;
            }
            #endif
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

        static unsigned long javan_count_threads(int started, int completed, int require_target, int exclude_current) {
            javan_runtime_lock_enter();
            unsigned long count = 0;
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                if (node->kind == JAVAN_HEAP_KIND_OBJECT && node->type_id == JAVAN_TYPE_JAVA_LANG_THREAD) {
                    javan_thread* thread = (javan_thread*) node->value;
                    if ((started < 0 || thread->started == started)
                        && (completed < 0 || thread->completed == completed)
                        && (require_target < 0 || (thread->target != NULL) == require_target)
                        && (exclude_current == 0 || node->value != javan_current_thread_value)) {
                        count++;
                    }
                }
                node = node->next;
            }
            javan_runtime_lock_leave();
            return count;
        }

        static int javan_thread_root_index(void* value) {
            for (int index = 0; index < javan_thread_root_count_value; index++) {
                if (javan_thread_roots_value[index] == value) {
                    return index;
                }
            }
            return -1;
        }

        static void javan_thread_root_ensure_capacity(int next_count) {
            if (next_count <= javan_thread_root_capacity_value) {
                return;
            }
            int next_capacity = javan_thread_root_capacity_value <= 0 ? 4 : javan_thread_root_capacity_value * 2;
            while (next_capacity < next_count) {
                next_capacity *= 2;
            }
            void** next_roots = (void**) javan_raw_calloc_retry(
                (unsigned long) next_capacity * sizeof(void*)
            );
            if (next_roots == NULL) {
                javan_panic("out of memory");
            }
            javan_root_frame*** next_frame_heads = (javan_root_frame***) javan_raw_calloc_retry(
                (unsigned long) next_capacity * sizeof(javan_root_frame**)
            );
            if (next_frame_heads == NULL) {
                free(next_roots);
                javan_panic("out of memory");
            }
            if (javan_thread_root_count_value > 0) {
                memcpy(
                    next_roots,
                    javan_thread_roots_value,
                    (unsigned long) javan_thread_root_count_value * sizeof(void*)
                );
                memcpy(
                    next_frame_heads,
                    javan_thread_root_frame_heads_value,
                    (unsigned long) javan_thread_root_count_value * sizeof(javan_root_frame**)
                );
            }
            free(javan_thread_roots_value);
            javan_thread_roots_value = next_roots;
            free(javan_thread_root_frame_heads_value);
            javan_thread_root_frame_heads_value = next_frame_heads;
            javan_thread_root_capacity_value = next_capacity;
            javan_heap_maybe_validate();
        }

        static void javan_thread_root_register(void* value) {
            javan_runtime_lock_enter();
            if (value == NULL) {
                javan_runtime_lock_leave();
                javan_panic("invalid thread root");
            }
            if (javan_thread_root_index(value) >= 0) {
                javan_runtime_lock_leave();
                javan_panic("thread root already registered");
            }
            javan_allocator_ensure_cleanup();
            void** javan_thread_root_register_roots[] = { &value };
            javan_root_frame_push(javan_thread_root_register_roots, 1);
            javan_thread_root_ensure_capacity(javan_thread_root_count_value + 1);
            javan_thread_roots_value[javan_thread_root_count_value] = value;
            javan_thread_root_frame_heads_value[javan_thread_root_count_value] = NULL;
            javan_thread_root_count_value++;
            javan_root_frame_pop(javan_thread_root_register_roots);
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        static void javan_thread_root_bind_current_frames(void* value) {
            javan_runtime_lock_enter();
            int index = javan_thread_root_index(value);
            if (index < 0) {
                javan_runtime_lock_leave();
                javan_panic("thread root not registered");
            }
            javan_thread_root_frame_heads_value[index] = &javan_root_frames_value;
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        static void javan_thread_root_unregister(void* value) {
            javan_runtime_lock_enter();
            int index = javan_thread_root_index(value);
            if (index < 0) {
                javan_runtime_lock_leave();
                javan_panic("thread root not registered");
            }
            javan_thread_root_count_value--;
            for (int next = index; next < javan_thread_root_count_value; next++) {
                javan_thread_roots_value[next] = javan_thread_roots_value[next + 1];
                javan_thread_root_frame_heads_value[next] = javan_thread_root_frame_heads_value[next + 1];
            }
            if (javan_thread_root_capacity_value > 0) {
                javan_thread_roots_value[javan_thread_root_count_value] = NULL;
                javan_thread_root_frame_heads_value[javan_thread_root_count_value] = NULL;
            }
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        unsigned long javan_heap_registered_thread_roots(void) {
            javan_runtime_lock_enter();
            unsigned long result = (unsigned long) javan_thread_root_count_value;
            javan_runtime_lock_leave();
            return result;
        }

        unsigned long javan_heap_thread_objects(void) {
            return javan_count_threads(-1, -1, -1, 0);
        }

        unsigned long javan_heap_started_threads(void) {
            return javan_count_threads(1, -1, -1, 0);
        }

        unsigned long javan_heap_completed_threads(void) {
            return javan_count_threads(-1, 1, -1, 0);
        }

        unsigned long javan_heap_active_threads(void) {
            return javan_count_threads(1, 0, -1, 1);
        }

        unsigned long javan_heap_threads_with_target(void) {
            return javan_count_threads(-1, -1, 1, 0);
        }

        int javan_heap_current_thread_root_present(void) {
            javan_runtime_lock_enter();
            int result = javan_current_thread_value != NULL
                && javan_find_allocation(javan_current_thread_value, NULL) != NULL
                && javan_thread_root_index(javan_current_thread_value) >= 0;
            javan_runtime_lock_leave();
            return result;
        }

        static javan_thread* javan_require_thread(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_THREAD) {
                javan_panic("not a Thread");
            }
            return (javan_thread*) value;
        }

        static void javan_thread_mark_started(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            thread->started = 1;
            thread->completed = 0;
        }

        static void javan_thread_mark_completed(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            thread->completed = 1;
            thread->park_permit = 0;
            thread->target = NULL;
            thread->thread_locals = NULL;
            javan_profile_thread_completion_count_value++;
        }

        static int javan_thread_has_live_lifecycle(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            return thread->started != 0 && thread->completed == 0;
        }

        static void javan_thread_completion_reset(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            #if defined(_WIN32)
            AcquireSRWLockExclusive(&thread->native_completion_lock);
            thread->native_completion_signaled = 0;
            ReleaseSRWLockExclusive(&thread->native_completion_lock);
            #else
            if (thread->native_sync_initialized == 0) {
                javan_panic("invalid Thread completion state");
            }
            if (pthread_mutex_lock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to acquire thread completion mutex");
            }
            thread->native_completion_signaled = 0;
            if (pthread_mutex_unlock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to release thread completion mutex");
            }
            #endif
        }

        static void javan_thread_completion_signal(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            #if defined(_WIN32)
            AcquireSRWLockExclusive(&thread->native_completion_lock);
            thread->native_completion_signaled = 1;
            WakeAllConditionVariable(&thread->native_completion_cond);
            ReleaseSRWLockExclusive(&thread->native_completion_lock);
            #else
            if (thread->native_sync_initialized == 0) {
                javan_panic("invalid Thread completion state");
            }
            if (pthread_mutex_lock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to acquire thread completion mutex");
            }
            thread->native_completion_signaled = 1;
            if (pthread_cond_broadcast(&thread->native_completion_cond) != 0) {
                pthread_mutex_unlock(&thread->native_completion_mutex);
                javan_panic("unable to signal thread completion");
            }
            if (pthread_mutex_unlock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to release thread completion mutex");
            }
            #endif
        }

        static int javan_thread_current_interrupted_peek(void) {
            javan_runtime_lock_enter();
            javan_thread* thread = javan_current_thread_object();
            int interrupted = thread->interrupted;
            javan_runtime_lock_leave();
            return interrupted;
        }

        static void javan_thread_enter_live_root(void* value) {
            javan_thread* thread = javan_require_thread(value);
            javan_thread_completion_reset(thread);
            javan_runtime_lock_enter();
            javan_thread_mark_started(thread);
            javan_runtime_lock_leave();
            javan_thread_root_register(value);
        }

        static void javan_thread_rollback_live_root(void* value) {
            javan_thread* thread = javan_require_thread(value);
            javan_runtime_lock_enter();
            thread->started = 0;
            thread->completed = 0;
            javan_runtime_lock_leave();
            javan_thread_root_unregister(value);
        }

        static void javan_thread_leave_live_root(void* value) {
            javan_runtime_lock_enter();
            javan_thread_mark_completed(javan_require_thread(value));
            javan_runtime_lock_leave();
            javan_thread_root_unregister(value);
        }

        static javan_thread* javan_thread_bootstrap_current(void) {
            javan_runtime_lock_enter();
            void* value = javan_thread_new();
            javan_current_thread_value = value;
            javan_thread_mark_started((javan_thread*) value);
            javan_thread_root_register(value);
            javan_thread_root_bind_current_frames(value);
            javan_thread_assign_name_text((javan_thread*) value, "main");
            javan_runtime_lock_leave();
            return (javan_thread*) value;
        }

        static javan_thread* javan_current_thread_object(void) {
            if (javan_current_thread_value == NULL) {
                return javan_thread_bootstrap_current();
            }
            return (javan_thread*) javan_current_thread_value;
        }

        void* javan_thread_current(void) {
            return (void*) javan_current_thread_object();
        }

        void* javan_thread_get_name(void* value) {
            javan_thread* thread = javan_require_thread(value);
            if (thread->name == NULL) {
                return javan_thread_copy_default_virtual_name();
            }
            return thread->name;
        }

        void javan_thread_set_name(void* value, void* name) {
            javan_thread* thread = javan_require_thread(value);
            if (name == NULL) {
                return;
            }
            thread->name = (char*) name;
        }

        void javan_thread_detach_current(void) {
            if (javan_current_thread_value == NULL) {
                return;
            }
            if (javan_root_frame_depth_value != 0 || javan_frame_root_count_value != 0) {
                javan_panic("cannot detach current thread with live root frames");
            }
            if (javan_native_resource_frames_value != NULL) {
                javan_panic("cannot detach current thread with live native resources");
            }
            javan_thread_leave_live_root(javan_current_thread_value);
            javan_current_thread_value = NULL;
        }

        void javan_thread_set_target(void* value, void* target) {
            javan_require_thread(value)->target = target;
        }

        static javan_thread_local* javan_require_thread_local(void* value) {
            if (value == NULL) {
                javan_panic("null ThreadLocal");
            }
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL) {
                javan_panic("unsupported ThreadLocal object");
            }
            return (javan_thread_local*) value;
        }

        static javan_object_map* javan_thread_local_storage(javan_thread* thread) {
            if (thread->thread_locals != NULL) {
                return javan_map_checked(thread->thread_locals);
            }
            void* thread_root = (void*) thread;
            void* map_value = NULL;
            void** javan_thread_local_storage_roots[] = {
                (void**) &thread_root,
                (void**) &map_value
            };
            javan_root_frame_push(javan_thread_local_storage_roots, 2);
            map_value = javan_hashmap_new();
            ((javan_thread*) thread_root)->thread_locals = map_value;
            javan_root_frame_pop(javan_thread_local_storage_roots);
            return javan_map_checked(map_value);
        }

        static int javan_map_find_identity(javan_object_map* map, void* key) {
            for (int index = 0; index < map->length; index++) {
                if (map->keys[index] == key) {
                    return index;
                }
            }
            return -1;
        }

        void* javan_thread_local_get(void* value) {
            javan_require_thread_local(value);
            javan_thread* thread = javan_current_thread_object();
            javan_runtime_lock_enter();
            javan_profile_thread_local_get_calls_value++;
            if (thread->thread_locals == NULL) {
                javan_runtime_lock_leave();
                return NULL;
            }
            javan_object_map* storage = javan_map_checked(thread->thread_locals);
            int index = javan_map_find_identity(storage, value);
            void* result = index < 0 ? NULL : storage->values[index];
            javan_runtime_lock_leave();
            return result;
        }

        void javan_thread_local_set(void* value, void* thread_local_value) {
            javan_require_thread_local(value);
            javan_thread* thread = javan_current_thread_object();
            javan_runtime_lock_enter();
            javan_profile_thread_local_set_calls_value++;
            javan_object_map* storage = javan_thread_local_storage(thread);
            int index = javan_map_find_identity(storage, value);
            if (index >= 0) {
                storage->values[index] = thread_local_value;
                javan_runtime_lock_leave();
                return;
            }
            void* key_root = value;
            void* element_root = thread_local_value;
            void** javan_thread_local_set_roots[] = {
                (void**) &storage,
                (void**) &key_root,
                (void**) &element_root
            };
            javan_root_frame_push(javan_thread_local_set_roots, 3);
            javan_map_ensure_capacity(storage, storage->length + 1);
            storage->keys[storage->length] = key_root;
            storage->values[storage->length] = element_root;
            storage->length++;
            storage->mod_count++;
            javan_root_frame_pop(javan_thread_local_set_roots);
            javan_runtime_lock_leave();
        }

        void javan_thread_local_remove(void* value) {
            javan_require_thread_local(value);
            javan_thread* thread = javan_current_thread_object();
            javan_runtime_lock_enter();
            javan_profile_thread_local_remove_calls_value++;
            if (thread->thread_locals == NULL) {
                javan_runtime_lock_leave();
                return;
            }
            javan_object_map* storage = javan_map_checked(thread->thread_locals);
            int index = javan_map_find_identity(storage, value);
            if (index < 0) {
                javan_runtime_lock_leave();
                return;
            }
            for (int cursor = index + 1; cursor < storage->length; cursor++) {
                storage->keys[cursor - 1] = storage->keys[cursor];
                storage->values[cursor - 1] = storage->values[cursor];
            }
            storage->length--;
            storage->keys[storage->length] = NULL;
            storage->values[storage->length] = NULL;
            storage->mod_count++;
            javan_runtime_lock_leave();
        }

        static void javan_thread_run_registered_target(void* value) {
            javan_thread* thread = javan_require_thread(value);
            void* target = thread->target;
            void** javan_thread_start_roots[] = { &value, &target };
            javan_root_frame_push(javan_thread_start_roots, 2);
            if (target != NULL) {
                javan_thread_run_target(target);
            }
            if (javan_root_frames_value != NULL && javan_root_frames_value->roots == javan_thread_start_roots) {
                javan_root_frame_pop(javan_thread_start_roots);
            }
        }

        #if defined(_WIN32)
        static unsigned __stdcall javan_thread_host_start(void* argument) {
        #else
        static void* javan_thread_host_start(void* argument) {
        #endif
            void* value = argument;
            javan_thread* thread = javan_require_thread(value);
            javan_current_thread_value = value;
            javan_thread_root_bind_current_frames(value);
            javan_thread_run_registered_target(value);
            javan_thread_leave_live_root(value);
            #if defined(_WIN32)
            if (thread->native_handle != NULL) {
                CloseHandle((HANDLE) thread->native_handle);
                thread->native_handle = NULL;
            }
            #endif
            javan_thread_completion_signal(thread);
            javan_current_thread_value = NULL;
            #if defined(_WIN32)
            return 0U;
            #else
            return NULL;
            #endif
        }

        static void javan_thread_wait_for_completion(javan_thread* thread) {
            #if defined(_WIN32)
            AcquireSRWLockExclusive(&thread->native_completion_lock);
            while (thread->started != 0 && thread->native_completion_signaled == 0) {
                if (SleepConditionVariableSRW(
                    &thread->native_completion_cond,
                    &thread->native_completion_lock,
                    INFINITE,
                    0
                ) == 0) {
                    ReleaseSRWLockExclusive(&thread->native_completion_lock);
                    javan_panic("Thread.join host wait failed");
                }
            }
            ReleaseSRWLockExclusive(&thread->native_completion_lock);
            #else
            if (thread->native_sync_initialized == 0) {
                javan_panic("invalid Thread completion state");
            }
            if (pthread_mutex_lock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to acquire thread completion mutex");
            }
            while (thread->started != 0 && thread->native_completion_signaled == 0) {
                if (pthread_cond_wait(&thread->native_completion_cond, &thread->native_completion_mutex) != 0) {
                    pthread_mutex_unlock(&thread->native_completion_mutex);
                    javan_panic("Thread.join host wait failed");
                }
            }
            if (pthread_mutex_unlock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to release thread completion mutex");
            }
            #endif
        }

        #if defined(__GNUC__) || defined(__clang__)
        __attribute__((weak))
        #endif
        void javan_thread_run_target(void* target) {
            (void) target;
            javan_panic("Thread.start with Runnable target has no closed-world Runnable.run implementation");
        }

        void javan_thread_sleep_millis(long long millis) {
            if (millis < 0) {
                javan_panic("negative Thread.sleep millis");
            }
            while (millis > 0) {
                long long chunk = millis > 60000LL ? 60000LL : millis;
                javan_sleep_micros((unsigned long) (chunk * 1000LL));
                millis -= chunk;
            }
        }

        int javan_thread_sleep_millis_interruptible(long long millis) {
            if (millis < 0) {
                javan_panic("negative Thread.sleep millis");
            }
            while (millis > 0) {
                if (javan_thread_current_interrupted_peek() != 0) {
                    (void) javan_thread_interrupted();
                    return 1;
                }
                long long chunk = millis > 5LL ? 5LL : millis;
                javan_sleep_micros((unsigned long) (chunk * 1000LL));
                millis -= chunk;
            }
            if (javan_thread_current_interrupted_peek() != 0) {
                (void) javan_thread_interrupted();
                return 1;
            }
            return 0;
        }

        int javan_thread_interrupted(void) {
            javan_runtime_lock_enter();
            javan_thread* thread = javan_current_thread_object();
            int interrupted = thread->interrupted;
            thread->interrupted = 0;
            javan_runtime_lock_leave();
            return interrupted;
        }

        void javan_thread_interrupt(void* value) {
            javan_runtime_lock_enter();
            javan_profile_thread_interrupt_calls_value++;
            javan_require_thread(value)->interrupted = 1;
            javan_runtime_lock_leave();
        }

        void javan_thread_park(void) {
            javan_runtime_lock_enter();
            javan_profile_thread_park_calls_value++;
            javan_runtime_lock_leave();
            while (1) {
                javan_runtime_lock_enter();
                javan_thread* thread = javan_current_thread_object();
                if (thread->interrupted != 0) {
                    javan_runtime_lock_leave();
                    return;
                }
                if (thread->park_permit != 0) {
                    thread->park_permit = 0;
                    javan_runtime_lock_leave();
                    return;
                }
                javan_runtime_lock_leave();
                javan_sleep_micros(5000UL);
            }
        }

        void javan_thread_park_nanos(long long nanos) {
            if (nanos <= 0LL) {
                return;
            }
            javan_runtime_lock_enter();
            javan_profile_thread_park_nanos_calls_value++;
            javan_runtime_lock_leave();
            long long started = javan_system_nano_time();
            while (1) {
                javan_runtime_lock_enter();
                javan_thread* thread = javan_current_thread_object();
                if (thread->interrupted != 0) {
                    javan_runtime_lock_leave();
                    return;
                }
                if (thread->park_permit != 0) {
                    thread->park_permit = 0;
                    javan_runtime_lock_leave();
                    return;
                }
                javan_runtime_lock_leave();
                long long elapsed = javan_system_nano_time() - started;
                if (elapsed >= nanos) {
                    return;
                }
                long long remaining = nanos - elapsed;
                long long chunk_nanos = remaining > 5000000LL ? 5000000LL : remaining;
                if (chunk_nanos <= 0LL) {
                    return;
                }
                javan_sleep_micros((unsigned long) ((chunk_nanos + 999LL) / 1000LL));
            }
        }

        void javan_thread_park_until(long long deadline_millis) {
            javan_runtime_lock_enter();
            javan_profile_thread_park_until_calls_value++;
            javan_runtime_lock_leave();
            while (1) {
                javan_runtime_lock_enter();
                javan_thread* thread = javan_current_thread_object();
                if (thread->interrupted != 0) {
                    javan_runtime_lock_leave();
                    return;
                }
                if (thread->park_permit != 0) {
                    thread->park_permit = 0;
                    javan_runtime_lock_leave();
                    return;
                }
                javan_runtime_lock_leave();
                long long now = javan_system_current_time_millis();
                if (now >= deadline_millis) {
                    return;
                }
                long long remaining_millis = deadline_millis - now;
                long long chunk_millis = remaining_millis > 5LL ? 5LL : remaining_millis;
                if (chunk_millis <= 0LL) {
                    return;
                }
                javan_sleep_micros((unsigned long) (chunk_millis * 1000LL));
            }
        }

        void javan_thread_unpark(void* value) {
            if (value == NULL) {
                return;
            }
            javan_runtime_lock_enter();
            javan_profile_thread_unpark_calls_value++;
            javan_require_thread(value)->park_permit = 1;
            javan_runtime_lock_leave();
        }

        int javan_thread_is_interrupted(void* value) {
            javan_runtime_lock_enter();
            int interrupted = javan_require_thread(value)->interrupted;
            javan_runtime_lock_leave();
            return interrupted;
        }

        int javan_thread_is_alive(void* value) {
            javan_runtime_lock_enter();
            javan_thread* thread = javan_require_thread(value);
            if (thread == javan_current_thread_object()) {
                javan_runtime_lock_leave();
                return 1;
            }
            int alive = javan_thread_has_live_lifecycle(thread);
            javan_runtime_lock_leave();
            return alive;
        }

        int javan_thread_is_virtual(void* value) {
            javan_runtime_lock_enter();
            int virtual_thread = javan_require_thread(value)->virtual_thread;
            javan_runtime_lock_leave();
            return virtual_thread;
        }

        void javan_thread_start(void* value) {
            javan_thread* thread = javan_require_thread(value);
            if (thread == javan_current_thread_object() || thread->started != 0) {
                javan_panic("Thread.start duplicate is not supported yet");
            }
            javan_thread_enter_live_root(value);
            javan_runtime_lock_enter();
            javan_profile_thread_start_calls_value++;
            javan_runtime_lock_leave();
            #if defined(_WIN32)
            thread->native_handle = (void*) _beginthreadex(NULL, 0, javan_thread_host_start, value, 0, NULL);
            if (thread->native_handle == NULL) {
                javan_thread_rollback_live_root(value);
                javan_panic("Thread.start host create failed");
            }
            #else
            pthread_attr_t attributes;
            if (pthread_attr_init(&attributes) != 0) {
                javan_thread_rollback_live_root(value);
                javan_panic("Thread.start host create failed");
            }
            if (pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED) != 0) {
                pthread_attr_destroy(&attributes);
                javan_thread_rollback_live_root(value);
                javan_panic("Thread.start host create failed");
            }
            pthread_t native_thread;
            if (pthread_create(&native_thread, &attributes, javan_thread_host_start, value) != 0) {
                pthread_attr_destroy(&attributes);
                javan_thread_rollback_live_root(value);
                javan_panic("Thread.start host create failed");
            }
            pthread_attr_destroy(&attributes);
            #endif
        }

        int javan_thread_join_interruptible(void* value) {
            javan_thread* thread = javan_require_thread(value);
            javan_thread* current = javan_current_thread_object();
            if (thread == current) {
                javan_panic("Thread.join on current thread is not supported yet");
            }
            javan_runtime_lock_enter();
            javan_profile_thread_join_calls_value++;
            javan_runtime_lock_leave();
            while (1) {
                if (javan_thread_current_interrupted_peek() != 0) {
                    (void) javan_thread_interrupted();
                    javan_runtime_lock_enter();
                    javan_profile_thread_join_interruptions_value++;
                    javan_runtime_lock_leave();
                    return 1;
                }
                javan_runtime_lock_enter();
                int done = thread->started == 0 || thread->native_completion_signaled != 0;
                javan_runtime_lock_leave();
                if (done != 0) {
                    return 0;
                }
                javan_sleep_micros(5000UL);
            }
        }

        void javan_thread_join(void* value) {
            javan_thread* thread = javan_require_thread(value);
            javan_thread* current = javan_current_thread_object();
            if (thread == current) {
                javan_panic("Thread.join on current thread is not supported yet");
            }
            javan_runtime_lock_enter();
            javan_profile_thread_join_calls_value++;
            javan_runtime_lock_leave();
            javan_thread_wait_for_completion(thread);
        }

        void javan_wait_for_non_current_threads(void) {
            while (1) {
                void* next = NULL;
                javan_runtime_lock_enter();
                javan_thread* current = javan_current_thread_object();
                for (int index = 0; index < javan_thread_root_count_value; index++) {
                    void* candidate = javan_thread_roots_value[index];
                    if (candidate == NULL || candidate == (void*) current) {
                        continue;
                    }
                    javan_thread* thread = (javan_thread*) candidate;
                    if (thread->started != 0 && thread->completed == 0) {
                        next = candidate;
                        break;
                    }
                }
                javan_runtime_lock_leave();
                if (next == NULL) {
                    return;
                }
                javan_thread_join(next);
            }
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY) {
                javan_virtual_thread_name_state* state = (javan_virtual_thread_name_state*) value;
                if (state != NULL
                    && ((runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                    && state->magic == JAVAN_VIRTUAL_THREAD_BUILDER_MAGIC)
                    || (runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY
                    && state->magic == JAVAN_VIRTUAL_THREAD_FACTORY_MAGIC))) {
                    javan_gc_mark_value(state->fixed_name);
                    javan_gc_mark_value(state->counter_prefix);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR) {
                javan_virtual_thread_executor_state* state = (javan_virtual_thread_executor_state*) value;
                if (state != NULL && state->magic == JAVAN_VIRTUAL_THREAD_EXECUTOR_MAGIC) {
                    javan_gc_mark_value(state->factory);
                    javan_gc_mark_value((void*) state->threads);
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
                if (node->type_id == JAVAN_TYPE_JAVA_LANG_THREAD) {
                    javan_gc_mark_value(((javan_thread*) value)->name);
                    javan_gc_mark_value(((javan_thread*) value)->target);
                    javan_gc_mark_value(((javan_thread*) value)->thread_locals);
                }
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
        }

        static void javan_gc_mark_thread_roots(void) {
            for (int index = 0; index < javan_thread_root_count_value; index++) {
                javan_gc_mark_value(javan_thread_roots_value[index]);
            }
        }

        static void javan_gc_mark_registered_thread_frame_roots(void) {
            for (int index = 0; index < javan_thread_root_count_value; index++) {
                javan_root_frame*** frame_head_slot = &javan_thread_root_frame_heads_value[index];
                if (frame_head_slot == NULL || *frame_head_slot == NULL) {
                    continue;
                }
                javan_root_frame* frame = **frame_head_slot;
                while (frame != NULL) {
                    for (int next = 0; next < frame->count; next++) {
                        void** slot = frame->roots[next];
                        if (slot != NULL) {
                            javan_gc_mark_value(*slot);
                        }
                    }
                    frame = frame->next;
                }
            }
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
                        if (node->type_id == JAVAN_TYPE_JAVA_LANG_THREAD) {
                            javan_release_thread_native_state((javan_thread*) node->value);
                        }
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
            javan_runtime_lock_enter();
            if (javan_gc_enabled_value == 0 || javan_gc_collecting != 0 || javan_allocator_cleaning != 0) {
                javan_runtime_lock_leave();
                return;
            }
            javan_gc_collecting = 1;
            javan_gc_collection_count_value++;
            javan_gc_clear_marks();
            javan_gc_mark_static_roots();
            javan_gc_mark_thread_roots();
            javan_gc_mark_registered_thread_frame_roots();
            javan_gc_mark_frame_roots();
            javan_gc_mark_runtime_object_references();
            javan_gc_sweep_unmarked();
            javan_gc_collecting = 0;
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
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
            javan_runtime_lock_enter();
            javan_gc_safe_point_init();
            if (javan_gc_safe_point_interval == 0) {
                javan_runtime_lock_leave();
                return;
            }
            javan_gc_safe_point_ticks++;
            if ((javan_gc_safe_point_ticks % javan_gc_safe_point_interval) == 0) {
                javan_gc_collect();
            }
            javan_runtime_lock_leave();
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
            if (left_type != 0 || right_type != 0) {
                return 0;
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

        void* javan_map_remove(void* value, void* key) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            int index = javan_map_find(map, key);
            if (index < 0) {
                return NULL;
            }
            void* previous = map->values[index];
            for (int cursor = index + 1; cursor < map->length; cursor++) {
                map->keys[cursor - 1] = map->keys[cursor];
                map->values[cursor - 1] = map->values[cursor];
            }
            map->length--;
            map->keys[map->length] = NULL;
            map->values[map->length] = NULL;
            map->mod_count++;
            return previous;
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
            #if defined(_WIN32)
            (void) cwd;
            (void) command_value;
            (void) timeout_millis;
            return javan_process_result_new(127, "", "process execution unsupported on Windows");
            #else
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
                javan_sleep_micros(10000UL);
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
            #endif
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

    private RuntimeSourceMemorySections() {
    }

    static String heap() {
        return SOURCE_HEAP;
    }

    static String heapAlloc() {
        String result = SOURCE_HEAP_ALLOC_HEAD;
        result = result + SOURCE_HEAP_ALLOC_EXECUTOR;
        result = result + SOURCE_HEAP_ALLOC_TAIL;
        return result;
    }

    static String arrays() {
        return SOURCE_ARRAYS;
    }

    static String collections() {
        return SOURCE_COLLECTIONS;
    }
}
