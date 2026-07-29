package javan.codegen;

final class RuntimeSourceMemorySections {
    private static final String SOURCE_HEAP_HEAD = """
        typedef struct javan_allocation_node {
            void* value;
            void* base;
            unsigned long size;
            int kind;
            int type_id;
            int collectible;
            int runtime_kind;
            unsigned int mark;
            const char* array_class_name;
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
        #define JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR 26
        #define JAVAN_RUNTIME_KIND_ATOMIC_LONG 27
        #define JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN 28
        #ifndef JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA
        #define JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA 29
        #endif
        #define JAVAN_RUNTIME_KIND_MAP_ENTRY 30
        #define JAVAN_RUNTIME_KIND_ATOMIC_INTEGER 31
        #define JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE 32
        #define JAVAN_RUNTIME_KIND_RESOURCE_INPUT_STREAM 33
        #define JAVAN_LIST_VIEW_UNMODIFIABLE 1
        #define JAVAN_LIST_VIEW_SET 2
        #define JAVAN_MAP_VIEW_UNMODIFIABLE 1
        #define JAVAN_BUILTIN_INSTANCEOF_COLLECTION 1
        #define JAVAN_BUILTIN_INSTANCEOF_MAP 2
        #define JAVAN_BUILTIN_INSTANCEOF_MAP_ENTRY 3
        #define JAVAN_BUILTIN_INSTANCEOF_OBJECT_ARRAY 4
        #define JAVAN_BUILTIN_INSTANCEOF_INT_ARRAY 5
        #define JAVAN_BUILTIN_INSTANCEOF_LONG_ARRAY 6
        #define JAVAN_BUILTIN_INSTANCEOF_FLOAT_ARRAY 7
        #define JAVAN_BUILTIN_INSTANCEOF_DOUBLE_ARRAY 8
        #define JAVAN_BUILTIN_INSTANCEOF_BYTE_ARRAY 9
        #define JAVAN_BUILTIN_INSTANCEOF_BOOLEAN_ARRAY 10
        #define JAVAN_BUILTIN_INSTANCEOF_SHORT_ARRAY 11
        #define JAVAN_BUILTIN_INSTANCEOF_CHAR_ARRAY 12

        typedef struct javan_object_list {
            int magic;
            int length;
            int capacity;
            int immutable;
            int mod_count;
            int view_flags;
            int reserved;
            struct javan_object_list* backing;
            void** values;
        } javan_object_list;

        typedef struct {
            int magic;
            int index;
            int expected_mod_count;
            int reserved;
            javan_object_list* list;
        } javan_object_iterator;

        typedef struct javan_object_map {
            int magic;
            int length;
            int capacity;
            int immutable;
            int mod_count;
            int view_flags;
            int reserved0;
            struct javan_object_map* backing;
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
            int inherit_inheritable_thread_locals;
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
            int core_pool_size;
            int closed;
            int reserved0;
            void* thread_factory;
            void* rejected_execution_handler;
            javan_object_list* threads;
        } javan_scheduled_thread_pool_executor_state;

        typedef struct {
            int magic;
            int reserved0;
            long long value;
        } javan_atomic_long_state;

        typedef struct {
            int magic;
            int value;
            int reserved0;
        } javan_atomic_integer_state;

        typedef struct {
            int magic;
            int value;
            int reserved0;
            int reserved1;
        } javan_atomic_boolean_state;

        typedef struct {
            int magic;
            int reserved0;
            void* value;
        } javan_atomic_reference_state;

        typedef struct {
            int magic;
            int builtin_kind;
            int reserved0;
            int reserved1;
        } javan_datetime_formatter_state;

        typedef struct {
            int magic;
            int stage;
            int reserved0;
            int reserved1;
        } javan_datetime_formatter_builder_state;

        typedef struct {
            int magic;
            int style_kind;
            int reserved0;
            int reserved1;
        } javan_text_style_state;

        typedef struct {
            int magic;
            int locale_kind;
            int reserved0;
            int reserved1;
        } javan_locale_state;

        typedef struct {
            int magic;
            int target_id;
            int capture_count;
            void** captures;
        } javan_materialized_lambda_state;

        typedef struct {
            int magic;
            int exact_type_id;
            int is_enum;
            int is_array;
            int assignable_count;
            int* assignable_type_ids;
            const char* binary_name;
        } javan_runtime_class_state;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            void* key;
            void* value;
        } javan_map_entry_state;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            char* host_address;
            char* host_name;
            char* canonical_host_name;
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
            int bound;
            int input_shutdown;
            int output_shutdown;
            int so_linger;
            int oob_inline;
            int traffic_class;
            int local_port;
            int remote_port;
            int so_timeout;
            int tcp_no_delay;
            int keep_alive;
            int reuse_address;
            int receive_buffer_size;
            int send_buffer_size;
            javan_inet_address* local_address;
            javan_inet_address* remote_address;
        } javan_socket;

        typedef struct {
            int magic;
            int fd;
            int bound;
            int closed;
            int local_port;
            int so_timeout;
            int reuse_address;
            int receive_buffer_size;
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
            int position;
            int length;
            int reserved0;
            void* bytes;
        } javan_resource_input_stream_value;

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
        #define JAVAN_RESOURCE_INPUT_STREAM_MAGIC 0x4a525349
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
        #define JAVAN_SCHEDULED_THREAD_POOL_EXECUTOR_MAGIC 0x4a535045
        #define JAVAN_ATOMIC_LONG_MAGIC 0x4a41544c
        #define JAVAN_ATOMIC_BOOLEAN_MAGIC 0x4a415442
        #define JAVAN_MATERIALIZED_LAMBDA_MAGIC 0x4a4d4c44
        #define JAVAN_MATERIALIZED_LAMBDA_MAX_CAPTURES 255
        #define JAVAN_MAP_ENTRY_MAGIC 0x4a4d454e
        #define JAVAN_ATOMIC_INTEGER_MAGIC 0x4a415449
        #define JAVAN_ATOMIC_REFERENCE_MAGIC 0x4a415452
        #define JAVAN_DATE_TIME_FORMATTER_MAGIC 0x4a445446
        #define JAVAN_DATE_TIME_FORMATTER_BUILDER_MAGIC 0x4a445442
        #define JAVAN_TEXT_STYLE_MAGIC 0x4a545354
        #define JAVAN_LOCALE_MAGIC 0x4a4c434c
        #define JAVAN_DATE_TIME_TEXT_STYLE_SHORT 1
        #define JAVAN_DATE_TIME_LOCALE_ENGLISH 1
        #define JAVAN_DATE_TIME_BUILDER_STAGE_NEW 0
        #define JAVAN_DATE_TIME_BUILDER_STAGE_CASE_INSENSITIVE 1
        #define JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_HEAD 2
        #define JAVAN_DATE_TIME_BUILDER_STAGE_ZONE_TEXT 3
        #define JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_TAIL 4
        #define JAVAN_DATE_TIME_FORMATTER_ISO_ZONED_DATE_TIME 1
        #define JAVAN_DATE_TIME_FORMATTER_ISO_OFFSET_DATE_TIME 2
        #define JAVAN_DATE_TIME_FORMATTER_ISO_ORDINAL_DATE 3
        #define JAVAN_DATE_TIME_FORMATTER_RFC_1123_DATE_TIME 4
        #define JAVAN_DATE_TIME_FORMATTER_ISO_LOCAL_DATE_TIME 5
        #define JAVAN_DATE_TIME_FORMATTER_ISO_OFFSET_DATE 6
        #define JAVAN_DATE_TIME_FORMATTER_ISO_LOCAL_TIME 7
        #define JAVAN_DATE_TIME_FORMATTER_ISO_OFFSET_TIME 8
        #define JAVAN_DATE_TIME_FORMATTER_ISO_LOCAL_DATE 9
        #define JAVAN_DATE_TIME_FORMATTER_BASIC_ISO_DATE 10
        #define JAVAN_DATE_TIME_FORMATTER_ISO_DATE_TIME 11
        #define JAVAN_DATE_TIME_FORMATTER_ISO_INSTANT 12
        #define JAVAN_DATE_TIME_FORMATTER_ISO_DATE 13
        #define JAVAN_DATE_TIME_FORMATTER_ISO_TIME 14
        #define JAVAN_DATE_TIME_FORMATTER_ISO_WEEK_DATE 15
        #define JAVAN_DATE_TIME_FORMATTER_DATE_TO_STRING 16
        #define JAVAN_HTTP_METHOD_GET 1
        #define JAVAN_HTTP_METHOD_POST 2
        #define JAVAN_HTTP_METHOD_PUT 3
        #define JAVAN_HTTP_BODY_KIND_STRING 1
        #define JAVAN_HTTP_BODY_KIND_BYTE_ARRAY 2
        #define JAVAN_CLASS_EXACT_STRING -2001
        #define JAVAN_CLASS_EXACT_OBJECT -2002
        #define JAVAN_CLASS_EXACT_CLASS -2003
        #define JAVAN_CLASS_EXACT_CLASS_LOADER -2004
        #define JAVAN_CLASS_EXACT_ARRAY_LIST -2005
        #define JAVAN_CLASS_EXACT_HASH_MAP -2006
        #define JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN -2007
        #define JAVAN_CLASS_EXACT_PRIMITIVE_BYTE -2008
        #define JAVAN_CLASS_EXACT_PRIMITIVE_SHORT -2009
        #define JAVAN_CLASS_EXACT_PRIMITIVE_CHAR -2010
        #define JAVAN_CLASS_EXACT_PRIMITIVE_INT -2011
        #define JAVAN_CLASS_EXACT_PRIMITIVE_LONG -2012
        #define JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT -2013
        #define JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE -2014
        #define JAVAN_CLASS_EXACT_PRIMITIVE_VOID -2015

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

        struct javan_object_handle {
            void* value;
            unsigned long references;
            struct javan_object_handle* next;
        };

        #define JAVAN_ROOT_FRAME_CACHE_LIMIT 32

        typedef void (*javan_native_resource_cleanup)(void* value);

        typedef struct javan_native_resource_frame {
            void* resource;
            javan_native_resource_cleanup cleanup;
            struct javan_native_resource_frame* next;
        } javan_native_resource_frame;

        typedef struct javan_thread javan_thread;

        #define JAVAN_ALLOCATION_CACHE_SIZE 4
        typedef struct {
            void** values;
            javan_allocation_node** nodes;
            int length;
            int capacity;
        } javan_allocation_registry;

        static javan_allocation_node* javan_allocations = NULL;
        static JavanObjectHandle* javan_object_handles = NULL;
        static void* javan_allocation_cache_values[JAVAN_ALLOCATION_CACHE_SIZE];
        static javan_allocation_node* javan_allocation_cache_nodes[JAVAN_ALLOCATION_CACHE_SIZE];
        static javan_allocation_registry javan_allocation_index = { NULL, NULL, 0, 0 };
        static int javan_allocator_cleanup_registered = 0;
        static int javan_allocator_cleaning = 0;
        static unsigned long javan_total_allocations_value = 0;
        static unsigned long javan_live_allocations_value = 0;
        static unsigned long javan_total_allocated_bytes_value = 0;
        static unsigned long javan_live_allocated_bytes_value = 0;
        static unsigned long javan_peak_live_allocated_bytes_value = 0;
        static JavanTypeDescriptor* javan_type_descriptors_value = NULL;
        static int javan_type_descriptor_count_value = 0;
        static int (*javan_record_object_equals_resolver_value)(void*, void*) = NULL;
        static int (*javan_record_object_hash_code_resolver_value)(void*) = NULL;
        static int (*javan_record_exact_type_resolver_value)(void*, int) = NULL;
        static void*** javan_static_roots_value = NULL;
        static int javan_static_root_count_value = 0;
        static JAVAN_THREAD_LOCAL javan_root_frame* javan_root_frames_value = NULL;
        static JAVAN_THREAD_LOCAL javan_root_frame* javan_root_frame_cache_value = NULL;
        static JAVAN_THREAD_LOCAL javan_native_resource_frame* javan_native_resource_frames_value = NULL;
        static JAVAN_THREAD_LOCAL JavanPanicScope* javan_panic_scope_top = NULL;
        static JAVAN_THREAD_LOCAL int javan_root_frame_depth_value = 0;
        static JAVAN_THREAD_LOCAL int javan_frame_root_count_value = 0;
        static JAVAN_THREAD_LOCAL int javan_root_frame_cache_count_value = 0;
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

        void javan_runtime_lock_enter(void) {
            if (javan_runtime_lock_ensure_initialized() == 0) {
                javan_panic("unable to initialize runtime lock");
            }
            EnterCriticalSection(&javan_runtime_lock_value);
            javan_runtime_lock_depth_value++;
        }

        void javan_runtime_lock_leave(void) {
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

        void javan_runtime_lock_enter(void) {
            if (pthread_once(&javan_runtime_lock_once, javan_runtime_lock_initialize) != 0) {
                javan_panic("unable to initialize runtime lock");
            }
            if (pthread_mutex_lock(&javan_runtime_lock_value) != 0) {
                javan_panic("unable to acquire runtime lock");
            }
            javan_runtime_lock_depth_value++;
        }

        void javan_runtime_lock_leave(void) {
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
        static void javan_object_handle_cleanup_all(void);
        static void javan_gc_mark_object_handles(void);
        static int javan_registered_type_id(void* value);
        static JavanTypeDescriptor* javan_type_descriptor_for(int type_id);
        static int javan_probably_string_key(void* value);
        static javan_materialized_lambda_state* javan_materialized_lambda_state_node_unlocked(void* value);
        static javan_materialized_lambda_state* javan_materialized_lambda_wrapper_state_unlocked(void* value);
        static int javan_materialized_lambda_is_instance_unlocked(void* value);

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

        static void javan_native_resource_cleanup_to(javan_native_resource_frame* frame_limit) {
            while (javan_native_resource_frames_value != frame_limit && javan_native_resource_frames_value != NULL) {
                javan_native_resource_frame* frame = javan_native_resource_frames_value;
                javan_native_resource_frames_value = frame->next;
                void* resource = frame->resource;
                javan_native_resource_cleanup cleanup = frame->cleanup;
                frame->resource = NULL;
                frame->cleanup = NULL;
                frame->next = NULL;
                if (resource != NULL && cleanup != NULL) {
                    cleanup(resource);
                }
            }
        }

        static javan_root_frame* javan_root_frame_take(void) {
            javan_root_frame* frame = javan_root_frame_cache_value;
            if (frame != NULL) {
                javan_root_frame_cache_value = frame->next;
                frame->next = NULL;
                javan_root_frame_cache_count_value--;
                return frame;
            }
            return (javan_root_frame*) malloc(sizeof(javan_root_frame));
        }

        static void javan_root_frame_release(javan_root_frame* frame) {
            if (frame == NULL) {
                return;
            }
            if (javan_root_frame_cache_count_value < JAVAN_ROOT_FRAME_CACHE_LIMIT) {
                frame->roots = NULL;
                frame->count = 0;
                frame->next = javan_root_frame_cache_value;
                javan_root_frame_cache_value = frame;
                javan_root_frame_cache_count_value++;
                return;
            }
            free(frame);
        }

        static void javan_root_frame_cache_cleanup(void) {
            javan_root_frame* frame = javan_root_frame_cache_value;
            while (frame != NULL) {
                javan_root_frame* next = frame->next;
                free(frame);
                frame = next;
            }
            javan_root_frame_cache_value = NULL;
            javan_root_frame_cache_count_value = 0;
        }

        static void javan_root_frame_cleanup(void) {
            javan_runtime_lock_enter();
            javan_root_frame* frame = javan_root_frames_value;
            while (frame != NULL) {
                javan_root_frame* next = frame->next;
                javan_root_frame_release(frame);
                frame = next;
            }
            javan_root_frames_value = NULL;
            javan_root_frame_depth_value = 0;
            javan_frame_root_count_value = 0;
            javan_runtime_lock_leave();
        }

        static void javan_root_frame_cleanup_to(
            javan_root_frame* frame_limit,
            int depth,
            int root_count
        ) {
            javan_runtime_lock_enter();
            while (javan_root_frames_value != frame_limit && javan_root_frames_value != NULL) {
                javan_root_frame* frame = javan_root_frames_value;
                javan_root_frames_value = frame->next;
                javan_root_frame_release(frame);
            }
            javan_root_frame_depth_value = depth;
            javan_frame_root_count_value = root_count;
            javan_runtime_lock_leave();
        }

        void javan_panic_scope_push(JavanPanicScope* scope, jmp_buf* target) {
            if (scope == NULL || target == NULL) {
                javan_panic("invalid panic scope");
            }
            scope->target = target;
            scope->previous_target = javan_panic_target;
            scope->source_context_top = javan_source_context_top;
            scope->root_frame_head = (void*) javan_root_frames_value;
            scope->root_frame_depth = javan_root_frame_depth_value;
            scope->frame_root_count = javan_frame_root_count_value;
            scope->native_resource_frame_head = (void*) javan_native_resource_frames_value;
            scope->previous = javan_panic_scope_top;
            javan_panic_scope_top = scope;
            javan_panic_target = target;
            javan_clear_error();
        }

        void javan_panic_scope_pop(JavanPanicScope* scope) {
            if (scope == NULL || javan_panic_scope_top != scope) {
                javan_panic("panic scope mismatch");
            }
            javan_panic_scope_top = scope->previous;
            javan_panic_target = scope->previous_target;
            scope->target = NULL;
            scope->previous_target = NULL;
            scope->source_context_top = NULL;
            scope->root_frame_head = NULL;
            scope->root_frame_depth = 0;
            scope->frame_root_count = 0;
            scope->native_resource_frame_head = NULL;
            scope->previous = NULL;
        }

        static int javan_panic_scope_recover_current(jmp_buf* target) {
            JavanPanicScope* scope = javan_panic_scope_top;
            if (scope == NULL || scope->target != target) {
                return 0;
            }
            javan_native_resource_cleanup_to((javan_native_resource_frame*) scope->native_resource_frame_head);
            javan_root_frame_cleanup_to(
                (javan_root_frame*) scope->root_frame_head,
                scope->root_frame_depth,
                scope->frame_root_count
            );
            javan_source_context_top = scope->source_context_top;
            javan_panic_scope_top = scope->previous;
            javan_panic_target = scope->previous_target;
            scope->target = NULL;
            scope->previous_target = NULL;
            scope->source_context_top = NULL;
            scope->root_frame_head = NULL;
            scope->root_frame_depth = 0;
            scope->frame_root_count = 0;
            scope->native_resource_frame_head = NULL;
            scope->previous = NULL;
            return 1;
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
            javan_root_frame_cache_cleanup();
            javan_thread_root_cleanup();
            javan_object_handle_cleanup_all();
            javan_object_registry_cleanup();
            for (int index = 0; index < JAVAN_ALLOCATION_CACHE_SIZE; index++) {
                javan_allocation_cache_values[index] = NULL;
                javan_allocation_cache_nodes[index] = NULL;
            }
            free(javan_allocation_index.values);
            free(javan_allocation_index.nodes);
            javan_allocation_index.values = NULL;
            javan_allocation_index.nodes = NULL;
            javan_allocation_index.length = 0;
            javan_allocation_index.capacity = 0;
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

        """;

    private static final String SOURCE_HEAP_TAIL_A = """
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

        static void javan_allocation_cache_remove(void* value) {
            if (value == NULL) {
                return;
            }
            for (int index = 0; index < JAVAN_ALLOCATION_CACHE_SIZE; index++) {
                if (javan_allocation_cache_values[index] == value) {
                    javan_allocation_cache_values[index] = NULL;
                    javan_allocation_cache_nodes[index] = NULL;
                }
            }
        }

        static void javan_allocation_cache_store(void* value, javan_allocation_node* node) {
            if (value == NULL || node == NULL) {
                return;
            }
            javan_allocation_cache_remove(value);
            for (int index = JAVAN_ALLOCATION_CACHE_SIZE - 1; index > 0; index--) {
                javan_allocation_cache_values[index] = javan_allocation_cache_values[index - 1];
                javan_allocation_cache_nodes[index] = javan_allocation_cache_nodes[index - 1];
            }
            javan_allocation_cache_values[0] = value;
            javan_allocation_cache_nodes[0] = node;
        }

        static javan_allocation_node* javan_allocation_cache_lookup(void* value) {
            if (value == NULL) {
                return NULL;
            }
            for (int index = 0; index < JAVAN_ALLOCATION_CACHE_SIZE; index++) {
                if (javan_allocation_cache_values[index] == value) {
                    javan_allocation_node* node = javan_allocation_cache_nodes[index];
                    if (index > 0 && node != NULL) {
                        for (int shift = index; shift > 0; shift--) {
                            javan_allocation_cache_values[shift] = javan_allocation_cache_values[shift - 1];
                            javan_allocation_cache_nodes[shift] = javan_allocation_cache_nodes[shift - 1];
                        }
                        javan_allocation_cache_values[0] = value;
                        javan_allocation_cache_nodes[0] = node;
                    }
                    return node;
                }
            }
            return NULL;
        }

        static void javan_allocation_registry_ensure_capacity(int required);
        static void javan_allocation_registry_put(void* value, javan_allocation_node* node);
        static void javan_allocation_registry_remove(void* value);
        static javan_allocation_node* javan_allocation_registry_lookup(void* value);

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
            node->array_class_name = NULL;
            node->next = javan_allocations;
            javan_allocations = node;
            javan_allocation_cache_store(value, node);
            javan_allocation_registry_put(value, node);
            javan_account_allocation(size);
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        static javan_allocation_node* javan_find_allocation(void* value, javan_allocation_node** previous) {
            if (value == NULL) {
                if (previous != NULL) {
                    *previous = NULL;
                }
                return NULL;
            }
            if (previous == NULL) {
                javan_allocation_node* cached = javan_allocation_cache_lookup(value);
                if (cached != NULL) {
                    return cached;
                }
                javan_allocation_node* indexed = javan_allocation_registry_lookup(value);
                if (indexed != NULL) {
                    javan_allocation_cache_store(value, indexed);
                    return indexed;
                }
            }
            javan_allocation_node* prior = NULL;
            javan_allocation_node* node = javan_allocations;
            while (node != NULL) {
                if (node->value == value) {
                    if (previous != NULL) {
                        *previous = prior;
                    }
                    javan_allocation_cache_store(value, node);
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

        void javan_register_record_object_method_resolvers(
            int (*equals_resolver)(void*, void*),
            int (*hash_code_resolver)(void*),
            int (*exact_type_resolver)(void*, int)
        ) {
            javan_runtime_lock_enter();
            javan_record_object_equals_resolver_value = equals_resolver;
            javan_record_object_hash_code_resolver_value = hash_code_resolver;
            javan_record_exact_type_resolver_value = exact_type_resolver;
            javan_runtime_lock_leave();
        }

        #define JAVAN_TYPE_JAVA_LANG_INTEGER -1001
        #define JAVAN_TYPE_JAVA_LANG_LONG -1002
        #define JAVAN_TYPE_JAVA_LANG_FLOAT -1003
        #define JAVAN_TYPE_JAVA_LANG_DOUBLE -1004
        #define JAVAN_TYPE_JAVA_LANG_BOOLEAN -1005
        #define JAVAN_TYPE_JAVA_LANG_BYTE -1015
        #define JAVAN_TYPE_JAVA_LANG_SHORT -1016
        #define JAVAN_TYPE_JAVA_LANG_CHARACTER -1014
        #define JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME -1006
        #define JAVAN_TYPE_JAVA_TIME_DURATION -1007
        #define JAVAN_TYPE_JAVA_LANG_THREAD -1008
        #define JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL -1009
        #define JAVAN_TYPE_JAVA_LANG_INHERITABLE_THREAD_LOCAL -1017
        #define JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER -1010
        #define JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER -1011
        #define JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE -1012
        #define JAVAN_TYPE_JAVA_UTIL_LOCALE -1013

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
                || type_id == JAVAN_TYPE_JAVA_LANG_BYTE
                || type_id == JAVAN_TYPE_JAVA_LANG_SHORT
                || type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER
                || type_id == JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME
                || type_id == JAVAN_TYPE_JAVA_TIME_DURATION
                || type_id == JAVAN_TYPE_JAVA_LANG_THREAD
                || type_id == JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL
                || type_id == JAVAN_TYPE_JAVA_LANG_INHERITABLE_THREAD_LOCAL
                || type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER
                || type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER
                || type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE
                || type_id == JAVAN_TYPE_JAVA_UTIL_LOCALE;
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
            if (kind != JAVAN_HEAP_KIND_ARRAY) {
                node->array_class_name = NULL;
            }
            javan_heap_maybe_validate();
            javan_runtime_lock_leave();
        }

        static void javan_update_array_class_name(void* value, const char* class_name) {
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_runtime_lock_leave();
                javan_panic("unknown array allocation");
            }
            if (node->kind != JAVAN_HEAP_KIND_ARRAY) {
                javan_runtime_lock_leave();
                javan_panic("invalid array allocation tag");
            }
            if (class_name == NULL || class_name[0] == '\\0') {
                javan_runtime_lock_leave();
                javan_panic("invalid array class name");
            }
            node->array_class_name = class_name;
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
                || runtime_kind == JAVAN_RUNTIME_KIND_RESOURCE_INPUT_STREAM
                || runtime_kind == JAVAN_RUNTIME_KIND_URI
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CLIENT
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_REQUEST
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_RESPONSE
                || runtime_kind == JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_LONG
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE
                || runtime_kind == JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA;
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
            javan_root_frame* frame = javan_root_frame_take();
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
            javan_root_frame_release(frame);
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

        static struct javan_object_header* javan_generated_object_header(void* value) {
            if (value == NULL) {
                return NULL;
            }
            int type_id = javan_registered_type_id(value);
            if (type_id <= 0) {
                return NULL;
            }
            return (struct javan_object_header*) value;
        }

        static void* javan_generated_object_runtime_state(void* value, int runtime_kind) {
            struct javan_object_header* header = javan_generated_object_header(value);
            if (header == NULL || header->_javan_runtime_state == NULL) {
                return NULL;
            }
            if (header->_javan_runtime_kind != runtime_kind) {
                javan_panic("invalid generated object runtime attachment");
            }
            return header->_javan_runtime_state;
        }

        static void javan_generated_object_attach_runtime_state(void* value, void* runtime_state, int runtime_kind) {
            struct javan_object_header* header = javan_generated_object_header(value);
            if (header == NULL) {
                javan_panic("unsupported generated object runtime attachment");
            }
            javan_allocation_node* node = javan_find_allocation(runtime_state, NULL);
            if (node == NULL || node->kind != JAVAN_HEAP_KIND_RUNTIME || node->runtime_kind != runtime_kind) {
                javan_panic("invalid generated object runtime attachment");
            }
            header->_javan_runtime_state = runtime_state;
            header->_javan_runtime_kind = runtime_kind;
            header->_javan_runtime_reserved = 0;
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
                javan_validate_runtime_managed_reference((void*) list->backing);
                javan_validate_owned_runtime_buffer_reference((void*) list->values);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP) {
                javan_object_map* map = (javan_object_map*) node->value;
                if (map->magic != JAVAN_OBJECT_MAP_MAGIC || map->length < 0 || map->capacity < 0 || map->length > map->capacity) {
                    javan_panic("invalid runtime map metadata");
                }
                javan_validate_runtime_managed_reference((void*) map->backing);
                javan_validate_owned_runtime_buffer_reference((void*) map->keys);
                javan_validate_owned_runtime_buffer_reference((void*) map->values);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                javan_string_builder* builder = (javan_string_builder*) node->value;
                if (builder->magic != JAVAN_STRING_BUILDER_MAGIC || builder->length < 0 || builder->capacity < 0 || builder->length > builder->capacity) {
                    javan_panic("invalid runtime string builder metadata");
                }
                if (builder->values != NULL && (builder->capacity < 0 || builder->length > builder->capacity)) {
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR) {
                javan_scheduled_thread_pool_executor_state* state = (javan_scheduled_thread_pool_executor_state*) node->value;
                if (state->magic != JAVAN_SCHEDULED_THREAD_POOL_EXECUTOR_MAGIC
                    || state->core_pool_size < 0
                    || (state->closed != 0 && state->closed != 1)) {
                    javan_panic("invalid runtime scheduled thread pool executor metadata");
                }
                javan_validate_runtime_managed_reference(state->thread_factory);
                javan_validate_runtime_managed_reference(state->rejected_execution_handler);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_LONG) {
                javan_atomic_long_state* state = (javan_atomic_long_state*) node->value;
                if (state->magic != JAVAN_ATOMIC_LONG_MAGIC) {
                    javan_panic("invalid runtime atomic long metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER) {
                javan_atomic_integer_state* state = (javan_atomic_integer_state*) node->value;
                if (state->magic != JAVAN_ATOMIC_INTEGER_MAGIC) {
                    javan_panic("invalid runtime atomic integer metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN) {
                javan_atomic_boolean_state* state = (javan_atomic_boolean_state*) node->value;
                if (state->magic != JAVAN_ATOMIC_BOOLEAN_MAGIC
                    || (state->value != 0 && state->value != 1)) {
                    javan_panic("invalid runtime atomic boolean metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE) {
                javan_atomic_reference_state* state = (javan_atomic_reference_state*) node->value;
                if (state->magic != JAVAN_ATOMIC_REFERENCE_MAGIC) {
                    javan_panic("invalid runtime atomic reference metadata");
                }
                javan_validate_runtime_managed_reference(state->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA) {
                javan_materialized_lambda_state* state =
                    javan_materialized_lambda_wrapper_state_unlocked(node->value);
                if (state != NULL) {
                    struct javan_object_header* header = (struct javan_object_header*) node->value;
                    javan_validate_runtime_managed_reference(header->_javan_runtime_state);
                } else {
                    state = javan_materialized_lambda_state_node_unlocked(node->value);
                    if (state == NULL) {
                        javan_panic("invalid materialized lambda metadata");
                    }
                    if (state->captures != NULL) {
                        javan_validate_owned_runtime_buffer_reference((void*) state->captures);
                    }
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_MAP_ENTRY) {
                javan_map_entry_state* state = (javan_map_entry_state*) node->value;
                if (state->magic != JAVAN_MAP_ENTRY_MAGIC) {
                    javan_panic("invalid map entry metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS) {
                javan_runtime_class_state* state = (javan_runtime_class_state*) node->value;
                if (state->magic != JAVAN_RUNTIME_CLASS_MAGIC
                    || state->binary_name == NULL
                    || state->binary_name[0] == '\\0'
                    || state->is_enum < 0
                    || state->is_array < 0
                    || state->assignable_count < 0
                    || (state->assignable_count > 0 && state->assignable_type_ids == NULL)) {
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
                if (address->magic != JAVAN_INET_ADDRESS_MAGIC
                    || address->host_address == NULL
                    || address->host_name == NULL
                    || address->canonical_host_name == NULL) {
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
                    || (socket->connected != 0 && socket->connected != 1)
                    || (socket->closed != 0 && socket->closed != 1)
                    || (socket->bound != 0 && socket->bound != 1)
                    || (socket->input_shutdown != 0 && socket->input_shutdown != 1)
                    || (socket->output_shutdown != 0 && socket->output_shutdown != 1)
                    || socket->local_port < -1
                    || socket->remote_port < 0
                    || socket->so_timeout < 0
                    || socket->receive_buffer_size <= 0
                    || socket->send_buffer_size <= 0
                    || (socket->tcp_no_delay != 0 && socket->tcp_no_delay != 1)
                    || (socket->keep_alive != 0 && socket->keep_alive != 1)
                    || (socket->reuse_address != 0 && socket->reuse_address != 1)
                    || socket->local_address == NULL
                    || (socket->connected != 0 && socket->remote_address == NULL)
                    || (socket->connected == 0 && socket->bound == 0 && socket->local_port != -1)
                    || (socket->connected == 0 && socket->remote_port != 0)) {
                    javan_panic("invalid runtime socket metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SERVER_SOCKET) {
                javan_server_socket* socket = (javan_server_socket*) node->value;
                if (socket->magic != JAVAN_SERVER_SOCKET_MAGIC
                    || socket->fd < -1
                    || (socket->bound != 0 && socket->bound != 1)
                    || (socket->closed != 0 && socket->closed != 1)
                    || socket->local_port < -1
                    || socket->so_timeout < 0
                    || socket->receive_buffer_size <= 0
                    || (socket->reuse_address != 0 && socket->reuse_address != 1)
                    || (socket->bound != 0 && socket->local_address == NULL)
                    || (socket->bound == 0 && socket->local_port != -1)) {
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_RESOURCE_INPUT_STREAM) {
                javan_resource_input_stream_value* stream = (javan_resource_input_stream_value*) node->value;
                if (stream->magic != JAVAN_RESOURCE_INPUT_STREAM_MAGIC
                    || stream->bytes == NULL
                    || stream->length < 0
                    || stream->position < 0
                    || stream->position > stream->length) {
                    javan_panic("invalid runtime resource input stream metadata");
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

        void* javan_printable_object_string(void* value) {
            if (value == NULL) {
                return (void*) "null";
            }
            if (javan_is_system_class_loader(value) != 0) {
                return (void*) "java.lang.ClassLoader$System";
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                return value;
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return value;
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER) {
                return javan_virtual_thread_builder_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY) {
                return javan_virtual_thread_factory_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR) {
                return javan_virtual_thread_executor_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS) {
                return javan_runtime_class_get_name(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS) {
                return javan_inet_socket_address_to_string(value);
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return javan_string_value_of_int(javan_integer_int_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return javan_string_value_of_long(javan_long_long_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_string_value_of_float(javan_float_float_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_string_value_of_double(javan_double_double_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return javan_string_value_of_bool(javan_boolean_boolean_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_BYTE) {
                return javan_string_value_of_int(javan_byte_byte_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_SHORT) {
                return javan_string_value_of_int(javan_short_short_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                return javan_string_value_of_char(javan_character_char_value(value));
            }
            if (node->type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER) {
                return (void*) "DateTimeFormatter";
            }
            if (node->type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER) {
                return (void*) "DateTimeFormatterBuilder";
            }
            if (node->type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE) {
                return (void*) "TextStyle";
            }
            if (node->type_id == JAVAN_TYPE_JAVA_UTIL_LOCALE) {
                return (void*) "Locale";
            }
            javan_panic("unsupported printable object");
            return (void*) "unsupported printable object";
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
                if (node->kind == JAVAN_HEAP_KIND_OBJECT && node->type_id > 0) {
                    struct javan_object_header* header = (struct javan_object_header*) node->value;
                    if ((header->_javan_runtime_state == NULL && header->_javan_runtime_kind != JAVAN_RUNTIME_KIND_NONE)
                        || (header->_javan_runtime_state != NULL && header->_javan_runtime_kind == JAVAN_RUNTIME_KIND_NONE)) {
                        javan_panic("invalid generated object runtime attachment");
                    }
                    if (header->_javan_runtime_state != NULL) {
                        javan_allocation_node* attached = javan_find_allocation(header->_javan_runtime_state, NULL);
                        if (attached == NULL
                            || attached->kind != JAVAN_HEAP_KIND_RUNTIME
                            || attached->runtime_kind != header->_javan_runtime_kind) {
                            javan_panic("invalid generated object runtime attachment");
                        }
                    }
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
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_CLASS
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_LONG
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_INTEGER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA) {
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
            uintptr_t original_address = (uintptr_t) value;
            void* next = realloc(value, actual_size);
            if (next == NULL) {
                javan_gc_collect();
                next = realloc(value, actual_size);
                if (next == NULL) {
                    javan_panic("out of memory");
                }
            }
            javan_account_realloc(node->size, actual_size);
            if ((uintptr_t) next != original_address) {
                javan_allocation_cache_remove((void*) original_address);
                javan_allocation_registry_remove((void*) original_address);
            }
            node->value = next;
            node->base = next;
            node->size = actual_size;
            javan_allocation_cache_store(next, node);
            javan_allocation_registry_put(next, node);
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
            javan_allocation_cache_remove(value);
            javan_allocation_registry_remove(value);
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
                    list->backing = NULL;
                    list->values = NULL;
                    list->capacity = 0;
                    list->length = 0;
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP) {
                javan_object_map* map = (javan_object_map*) node->value;
                if (map != NULL && map->magic == JAVAN_OBJECT_MAP_MAGIC) {
                    javan_free_owned_runtime_buffer((void*) map->keys);
                    javan_free_owned_runtime_buffer((void*) map->values);
                    map->backing = NULL;
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
        int javan_map_remove_entry(void* value, void* key, void* expected_value);
        void* javan_materialized_lambda_new(int target_id);
        int javan_materialized_lambda_target_id(void* value);
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
            javan_allocation_cache_remove(value);
            javan_allocation_registry_remove(value);
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
        """;

    private static final String SOURCE_HEAP_TAIL_B = """
        static void javan_allocation_registry_reinsert(
            void** values,
            javan_allocation_node** nodes,
            int capacity,
            void* value,
            javan_allocation_node* node
        ) {
            int index = javan_registry_slot(values, capacity, value);
            values[index] = value;
            nodes[index] = node;
        }

        static void javan_allocation_registry_ensure_capacity(int required) {
            if ((required * 2) < javan_allocation_index.capacity) {
                return;
            }
            int old_capacity = javan_allocation_index.capacity;
            void** old_values = javan_allocation_index.values;
            javan_allocation_node** old_nodes = javan_allocation_index.nodes;
            int next_capacity = old_capacity <= 0 ? 128 : old_capacity * 2;
            while ((required * 2) >= next_capacity) {
                next_capacity *= 2;
            }
            void** next_values = (void**) javan_raw_calloc_retry((unsigned long) next_capacity * sizeof(void*));
            if (next_values == NULL) {
                javan_panic("out of memory");
            }
            javan_allocation_node** next_nodes = (javan_allocation_node**) javan_raw_calloc_retry(
                (unsigned long) next_capacity * sizeof(javan_allocation_node*)
            );
            if (next_nodes == NULL) {
                free(next_values);
                javan_panic("out of memory");
            }
            for (int index = 0; index < old_capacity; index++) {
                if (old_values != NULL && old_values[index] != NULL && old_nodes[index] != NULL) {
                    javan_allocation_registry_reinsert(next_values, next_nodes, next_capacity, old_values[index], old_nodes[index]);
                }
            }
            free(old_values);
            free(old_nodes);
            javan_allocation_index.values = next_values;
            javan_allocation_index.nodes = next_nodes;
            javan_allocation_index.capacity = next_capacity;
        }

        static void javan_allocation_registry_put(void* value, javan_allocation_node* node) {
            if (value == NULL || node == NULL) {
                return;
            }
            javan_allocation_registry_ensure_capacity(javan_allocation_index.length + 1);
            int index = javan_registry_slot(javan_allocation_index.values, javan_allocation_index.capacity, value);
            if (javan_allocation_index.values[index] == NULL) {
                javan_allocation_index.length++;
            }
            javan_allocation_index.values[index] = value;
            javan_allocation_index.nodes[index] = node;
        }

        static void javan_allocation_registry_remove(void* value) {
            if (value == NULL || javan_allocation_index.capacity <= 0) {
                return;
            }
            int index = javan_registry_slot(javan_allocation_index.values, javan_allocation_index.capacity, value);
            if (javan_allocation_index.values[index] != value) {
                return;
            }
            javan_allocation_index.values[index] = NULL;
            javan_allocation_index.nodes[index] = NULL;
            javan_allocation_index.length--;
            int next = (index + 1) & (javan_allocation_index.capacity - 1);
            while (javan_allocation_index.values[next] != NULL) {
                void* moved_value = javan_allocation_index.values[next];
                javan_allocation_node* moved_node = javan_allocation_index.nodes[next];
                javan_allocation_index.values[next] = NULL;
                javan_allocation_index.nodes[next] = NULL;
                javan_allocation_index.length--;
                javan_allocation_registry_reinsert(
                    javan_allocation_index.values,
                    javan_allocation_index.nodes,
                    javan_allocation_index.capacity,
                    moved_value,
                    moved_node
                );
                javan_allocation_index.length++;
                next = (next + 1) & (javan_allocation_index.capacity - 1);
            }
        }

        static javan_allocation_node* javan_allocation_registry_lookup(void* value) {
            if (value == NULL || javan_allocation_index.capacity <= 0) {
                return NULL;
            }
            int index = javan_registry_slot(javan_allocation_index.values, javan_allocation_index.capacity, value);
            if (javan_allocation_index.values[index] != value) {
                return NULL;
            }
            return javan_allocation_index.nodes[index];
        }
        """;

    private static final String SOURCE_HEAP_TAIL_C = """
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

        int javan_object_builtin_instance_of(void* value, int target) {
            if (value == NULL) {
                return 0;
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                return 0;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_COLLECTION) {
                return node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_MAP) {
                return node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_MAP_ENTRY) {
                return node->runtime_kind == JAVAN_RUNTIME_KIND_MAP_ENTRY;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_OBJECT_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_OBJECT;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_INT_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_INT;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_LONG_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_LONG;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_FLOAT_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_FLOAT;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_DOUBLE_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_DOUBLE;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_BYTE_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_BYTE;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_BOOLEAN_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_BOOLEAN;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_SHORT_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_SHORT;
            }
            if (target == JAVAN_BUILTIN_INSTANCEOF_CHAR_ARRAY) {
                return node->kind == JAVAN_HEAP_KIND_ARRAY && node->type_id == JAVAN_ARRAY_KIND_CHAR;
            }
            javan_panic("unsupported builtin instanceof target");
            return 0;
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
            int value;
        } javan_boxed_byte;

        typedef struct {
            int value;
        } javan_boxed_short;

        typedef struct {
            int value;
        } javan_boxed_character;

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
            int inheritable;
        } javan_thread_local;

        typedef struct javan_thread {
            int interrupted;
            int started;
            int completed;
            int future_cancelled;
            int virtual_thread;
            int inherit_inheritable_thread_locals;
            int daemon;
            int priority;
            int park_permit;
            int schedule_mode;
            int scheduled_first_run_started;
            long long id;
            char* name;
            long long scheduled_initial_delay_nanos;
            long long scheduled_period_nanos;
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
            void* scheduled_executor;
            void* thread_locals;
        } javan_thread;

        static long long javan_platform_thread_name_counter_value = 0;
        static long long javan_thread_id_counter_value = 1;

        static long long javan_thread_next_id(void) {
            long long next = 0;
            javan_runtime_lock_enter();
            next = javan_thread_id_counter_value;
            javan_thread_id_counter_value++;
            javan_runtime_lock_leave();
            return next;
        }

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

        static javan_scheduled_thread_pool_executor_state* javan_scheduled_thread_pool_executor_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported scheduled thread pool executor");
            }
            void* attached = javan_generated_object_runtime_state(value, JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR);
            javan_scheduled_thread_pool_executor_state* state = (javan_scheduled_thread_pool_executor_state*) (attached == NULL ? value : attached);
            if (state->magic != JAVAN_SCHEDULED_THREAD_POOL_EXECUTOR_MAGIC || state->core_pool_size < 0) {
                javan_panic("unsupported scheduled thread pool executor");
            }
            return state;
        }

        static javan_atomic_long_state* javan_atomic_long_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic long");
            }
            void* attached = javan_generated_object_runtime_state(value, JAVAN_RUNTIME_KIND_ATOMIC_LONG);
            javan_atomic_long_state* state = (javan_atomic_long_state*) (attached == NULL ? value : attached);
            if (state->magic != JAVAN_ATOMIC_LONG_MAGIC) {
                javan_panic("unsupported atomic long");
            }
            return state;
        }

        static javan_atomic_integer_state* javan_atomic_integer_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic integer");
            }
            void* attached = javan_generated_object_runtime_state(value, JAVAN_RUNTIME_KIND_ATOMIC_INTEGER);
            javan_atomic_integer_state* state = (javan_atomic_integer_state*) (attached == NULL ? value : attached);
            if (state->magic != JAVAN_ATOMIC_INTEGER_MAGIC) {
                javan_panic("unsupported atomic integer");
            }
            return state;
        }

        static javan_atomic_boolean_state* javan_atomic_boolean_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic boolean");
            }
            void* attached = javan_generated_object_runtime_state(value, JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN);
            javan_atomic_boolean_state* state = (javan_atomic_boolean_state*) (attached == NULL ? value : attached);
            if (state->magic != JAVAN_ATOMIC_BOOLEAN_MAGIC || (state->value != 0 && state->value != 1)) {
                javan_panic("unsupported atomic boolean");
            }
            return state;
        }

        static javan_atomic_reference_state* javan_atomic_reference_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic reference");
            }
            void* attached = javan_generated_object_runtime_state(value, JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE);
            javan_atomic_reference_state* state = (javan_atomic_reference_state*) (attached == NULL ? value : attached);
            if (state->magic != JAVAN_ATOMIC_REFERENCE_MAGIC) {
                javan_panic("unsupported atomic reference");
            }
            return state;
        }

        static javan_runtime_class_state* javan_runtime_class_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported runtime class");
            }
            javan_runtime_class_state* state = (javan_runtime_class_state*) value;
            if (state->magic != JAVAN_RUNTIME_CLASS_MAGIC
                || state->binary_name == NULL
                || state->binary_name[0] == '\\0'
                || state->assignable_count < 0
                || (state->assignable_count > 0 && state->assignable_type_ids == NULL)) {
                javan_panic("unsupported runtime class");
            }
            return state;
        }

        static javan_map_entry_state* javan_map_entry_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported map entry");
            }
            javan_map_entry_state* state = (javan_map_entry_state*) value;
            if (state->magic != JAVAN_MAP_ENTRY_MAGIC) {
                javan_panic("unsupported map entry");
            }
            return state;
        }

        static void* javan_virtual_thread_name_state_new(int runtime_kind, int magic) {
            javan_virtual_thread_name_state* state = (javan_virtual_thread_name_state*) javan_alloc(sizeof(javan_virtual_thread_name_state));
            state->magic = magic;
            state->counter_mode = 0;
            state->closed = 0;
            state->inherit_inheritable_thread_locals = 1;
            state->next_counter = 0;
            state->fixed_name = NULL;
            state->counter_prefix = NULL;
            javan_update_runtime_allocation_kind((void*) state, runtime_kind);
            return state;
        }

        static void* javan_runtime_class_new(const char* binary_name) {
            return javan_runtime_class_literal(binary_name, 0, 0, 0, 0);
        }

        void* javan_runtime_class_literal(
            const char* binary_name,
            int exact_type_id,
            int is_enum,
            int is_array,
            int assignable_count,
            ...
        ) {
            if (binary_name == NULL || binary_name[0] == '\\0' || assignable_count < 0) {
                javan_panic("invalid runtime class name");
            }
            unsigned long binary_name_length = (unsigned long) strlen(binary_name) + 1UL;
            unsigned long assignable_offset = sizeof(javan_runtime_class_state) + binary_name_length;
            unsigned long assignable_alignment = sizeof(int);
            if ((assignable_offset % assignable_alignment) != 0UL) {
                assignable_offset += assignable_alignment - (assignable_offset % assignable_alignment);
            }
            unsigned long assignable_bytes = assignable_count > 0
                ? (unsigned long) assignable_count * sizeof(int)
                : 0UL;
            void* state_root = javan_alloc(assignable_offset + assignable_bytes);
            javan_runtime_class_state* state = (javan_runtime_class_state*) state_root;
            char* stored_binary_name = ((char*) state_root) + sizeof(javan_runtime_class_state);
            state->magic = JAVAN_RUNTIME_CLASS_MAGIC;
            state->exact_type_id = exact_type_id;
            state->is_enum = is_enum != 0 ? 1 : 0;
            state->is_array = is_array != 0 ? 1 : 0;
            state->assignable_count = assignable_count;
            state->assignable_type_ids = NULL;
            memcpy(stored_binary_name, binary_name, binary_name_length);
            state->binary_name = stored_binary_name;
            if (assignable_count > 0) {
                state->assignable_type_ids = (int*) (((char*) state_root) + assignable_offset);
                va_list arguments;
                va_start(arguments, assignable_count);
                for (int index = 0; index < assignable_count; index++) {
                    state->assignable_type_ids[index] = va_arg(arguments, int);
                }
                va_end(arguments);
            }
            javan_update_runtime_allocation_kind(state_root, JAVAN_RUNTIME_KIND_CLASS);
            return state_root;
        }

        static int javan_runtime_class_primitive_descriptor_char(int exact_type_id) {
            switch (exact_type_id) {
                case JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN:
                    return 'Z';
                case JAVAN_CLASS_EXACT_PRIMITIVE_BYTE:
                    return 'B';
                case JAVAN_CLASS_EXACT_PRIMITIVE_SHORT:
                    return 'S';
                case JAVAN_CLASS_EXACT_PRIMITIVE_CHAR:
                    return 'C';
                case JAVAN_CLASS_EXACT_PRIMITIVE_INT:
                    return 'I';
                case JAVAN_CLASS_EXACT_PRIMITIVE_LONG:
                    return 'J';
                case JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT:
                    return 'F';
                case JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE:
                    return 'D';
                case JAVAN_CLASS_EXACT_PRIMITIVE_VOID:
                    return 'V';
                default:
                    return 0;
            }
        }

        static const char* javan_runtime_class_primitive_array_binary_name(int exact_type_id) {
            switch (exact_type_id) {
                case JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN:
                    return "[Z";
                case JAVAN_CLASS_EXACT_PRIMITIVE_BYTE:
                    return "[B";
                case JAVAN_CLASS_EXACT_PRIMITIVE_SHORT:
                    return "[S";
                case JAVAN_CLASS_EXACT_PRIMITIVE_CHAR:
                    return "[C";
                case JAVAN_CLASS_EXACT_PRIMITIVE_INT:
                    return "[I";
                case JAVAN_CLASS_EXACT_PRIMITIVE_LONG:
                    return "[J";
                case JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT:
                    return "[F";
                case JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE:
                    return "[D";
                default:
                    return NULL;
            }
        }

        static int javan_runtime_class_is_primitive_exact_type_id(int exact_type_id) {
            switch (exact_type_id) {
                case JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN:
                case JAVAN_CLASS_EXACT_PRIMITIVE_BYTE:
                case JAVAN_CLASS_EXACT_PRIMITIVE_SHORT:
                case JAVAN_CLASS_EXACT_PRIMITIVE_CHAR:
                case JAVAN_CLASS_EXACT_PRIMITIVE_INT:
                case JAVAN_CLASS_EXACT_PRIMITIVE_LONG:
                case JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT:
                case JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE:
                case JAVAN_CLASS_EXACT_PRIMITIVE_VOID:
                    return 1;
                default:
                    return 0;
            }
        }

        static int javan_runtime_class_known_exact_type_id(const char* binary_name) {
            if (strcmp(binary_name, "java.lang.String") == 0) {
                return JAVAN_CLASS_EXACT_STRING;
            }
            if (strcmp(binary_name, "java.lang.Object") == 0) {
                return JAVAN_CLASS_EXACT_OBJECT;
            }
            if (strcmp(binary_name, "java.lang.Class") == 0) {
                return JAVAN_CLASS_EXACT_CLASS;
            }
            if (strcmp(binary_name, "java.lang.ClassLoader") == 0) {
                return JAVAN_CLASS_EXACT_CLASS_LOADER;
            }
            if (strcmp(binary_name, "java.util.ArrayList") == 0) {
                return JAVAN_CLASS_EXACT_ARRAY_LIST;
            }
            if (strcmp(binary_name, "java.util.HashMap") == 0) {
                return JAVAN_CLASS_EXACT_HASH_MAP;
            }
            if (strcmp(binary_name, "boolean") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN;
            }
            if (strcmp(binary_name, "byte") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_BYTE;
            }
            if (strcmp(binary_name, "short") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_SHORT;
            }
            if (strcmp(binary_name, "char") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_CHAR;
            }
            if (strcmp(binary_name, "int") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_INT;
            }
            if (strcmp(binary_name, "long") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_LONG;
            }
            if (strcmp(binary_name, "float") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT;
            }
            if (strcmp(binary_name, "double") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE;
            }
            if (strcmp(binary_name, "void") == 0) {
                return JAVAN_CLASS_EXACT_PRIMITIVE_VOID;
            }
            return 0;
        }

        static int javan_runtime_class_type_id_for_binary_name(const char* binary_name) {
            for (int index = 0; index < javan_type_descriptor_count_value; index++) {
                JavanTypeDescriptor* descriptor = &javan_type_descriptors_value[index];
                if (descriptor->name != NULL && strcmp(descriptor->name, binary_name) == 0) {
                    return descriptor->type_id;
                }
            }
            return 0;
        }

        static void* javan_runtime_class_from_binary_name(const char* binary_name) {
            int exact_type_id = javan_runtime_class_known_exact_type_id(binary_name);
            if (exact_type_id != 0) {
                return javan_runtime_class_literal(binary_name, exact_type_id, 0, binary_name[0] == '[' ? 1 : 0, 0);
            }
            int type_id = javan_runtime_class_type_id_for_binary_name(binary_name);
            if (type_id != 0) {
                JavanTypeDescriptor* descriptor = javan_type_descriptor_for(type_id);
                if (descriptor != NULL) {
                    return javan_runtime_class_literal(binary_name, type_id, descriptor->is_enum, 0, 1, type_id);
                }
            }
            return javan_runtime_class_literal(binary_name, 0, 0, binary_name[0] == '[' ? 1 : 0, 0);
        }

        static void* javan_runtime_class_component_type_from_binary_name(const char* binary_name) {
            if (binary_name == NULL || binary_name[0] != '[') {
                return NULL;
            }
            const char* component_descriptor = binary_name + 1;
            if (component_descriptor[0] == '[') {
                return javan_runtime_class_from_binary_name(component_descriptor);
            }
            if (component_descriptor[0] == 'L') {
                unsigned long descriptor_length = (unsigned long) strlen(component_descriptor);
                if (descriptor_length < 2UL || component_descriptor[descriptor_length - 1UL] != ';') {
                    javan_panic("unsupported array class metadata");
                }
                unsigned long binary_length = descriptor_length - 2UL;
                char* component_binary_name = (char*) javan_raw_calloc_retry(binary_length + 1UL);
                if (component_binary_name == NULL) {
                    javan_panic("out of memory");
                }
                memcpy(component_binary_name, component_descriptor + 1, binary_length);
                component_binary_name[binary_length] = '\\0';
                void* result = javan_runtime_class_from_binary_name(component_binary_name);
                free(component_binary_name);
                return result;
            }
            switch (component_descriptor[0]) {
                case 'Z':
                    return javan_runtime_class_literal("boolean", JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN, 0, 0, 0);
                case 'B':
                    return javan_runtime_class_literal("byte", JAVAN_CLASS_EXACT_PRIMITIVE_BYTE, 0, 0, 0);
                case 'S':
                    return javan_runtime_class_literal("short", JAVAN_CLASS_EXACT_PRIMITIVE_SHORT, 0, 0, 0);
                case 'C':
                    return javan_runtime_class_literal("char", JAVAN_CLASS_EXACT_PRIMITIVE_CHAR, 0, 0, 0);
                case 'I':
                    return javan_runtime_class_literal("int", JAVAN_CLASS_EXACT_PRIMITIVE_INT, 0, 0, 0);
                case 'J':
                    return javan_runtime_class_literal("long", JAVAN_CLASS_EXACT_PRIMITIVE_LONG, 0, 0, 0);
                case 'F':
                    return javan_runtime_class_literal("float", JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT, 0, 0, 0);
                case 'D':
                    return javan_runtime_class_literal("double", JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE, 0, 0, 0);
                default:
                    javan_panic("unsupported array class metadata");
                    return NULL;
            }
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

        void* javan_virtual_thread_builder_inherit_inheritable_thread_locals(void* value, int enabled) {
            javan_virtual_thread_name_state* state = javan_virtual_thread_builder_checked(value);
            state->inherit_inheritable_thread_locals = enabled != 0 ? 1 : 0;
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
            factory->inherit_inheritable_thread_locals = builder->inherit_inheritable_thread_locals;
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
            ((javan_thread*) thread_value)->inherit_inheritable_thread_locals = ((javan_virtual_thread_name_state*) state_root)->inherit_inheritable_thread_locals;
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

        static int javan_runtime_class_accepts_type_id(javan_runtime_class_state* state, int type_id) {
            if (state == NULL || type_id == 0) {
                return 0;
            }
            if (state->exact_type_id == type_id) {
                return 1;
            }
            for (int index = 0; index < state->assignable_count; index++) {
                if (state->assignable_type_ids[index] == type_id) {
                    return 1;
                }
            }
            return 0;
        }

        static int javan_runtime_class_builtin_instance(javan_runtime_class_state* state, void* object_value) {
            if (state->exact_type_id == JAVAN_CLASS_EXACT_OBJECT) {
                return object_value != NULL ? 1 : 0;
            }
            if (state->exact_type_id == JAVAN_CLASS_EXACT_STRING) {
                javan_allocation_node* node = javan_find_allocation(object_value, NULL);
                if (node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                    return 1;
                }
                if (node == NULL && javan_registered_type_id(object_value) == 0 && javan_probably_string_key(object_value) != 0) {
                    return 1;
                }
                return 0;
            }
            if (state->exact_type_id == JAVAN_CLASS_EXACT_ARRAY_LIST) {
                javan_allocation_node* node = javan_find_allocation(object_value, NULL);
                return node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST ? 1 : 0;
            }
            if (state->exact_type_id == JAVAN_CLASS_EXACT_HASH_MAP) {
                javan_allocation_node* node = javan_find_allocation(object_value, NULL);
                return node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP ? 1 : 0;
            }
            if (state->exact_type_id == JAVAN_CLASS_EXACT_CLASS) {
                javan_allocation_node* node = javan_find_allocation(object_value, NULL);
                return node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS ? 1 : 0;
            }
            if (state->exact_type_id == JAVAN_CLASS_EXACT_CLASS_LOADER) {
                return javan_is_system_class_loader(object_value);
            }
            if (state->is_array != 0) {
                javan_allocation_node* node = javan_find_allocation(object_value, NULL);
                if (node == NULL || node->kind != JAVAN_HEAP_KIND_ARRAY) {
                    return 0;
                }
                return javan_class_is_assignable_from((void*) state, javan_object_get_class(object_value));
            }
            return 0;
        }

        int javan_class_is_instance(void* class_value, void* object_value) {
            if (object_value == NULL) {
                return 0;
            }
            javan_runtime_class_state* state = javan_runtime_class_checked(class_value);
            if (javan_runtime_class_builtin_instance(state, object_value) != 0) {
                return 1;
            }
            int object_type_id = javan_registered_type_id(object_value);
            if (object_type_id != 0) {
                return javan_runtime_class_accepts_type_id(state, object_type_id);
            }
            return 0;
        }

        void* javan_class_cast(void* class_value, void* object_value) {
            if (object_value == NULL) {
                return NULL;
            }
            if (javan_class_is_instance(class_value, object_value) != 0) {
                return object_value;
            }
            javan_panic("Class.cast type mismatch");
            return NULL;
        }

        int javan_class_is_enum(void* class_value) {
            return javan_runtime_class_checked(class_value)->is_enum;
        }

        int javan_class_is_array(void* class_value) {
            return javan_runtime_class_checked(class_value)->is_array;
        }

        int javan_class_is_primitive(void* class_value) {
            return javan_runtime_class_is_primitive_exact_type_id(javan_runtime_class_checked(class_value)->exact_type_id);
        }

        int javan_class_is_assignable_from(void* target, void* source) {
            void* target_root = target;
            void* source_root = source;
            void* target_component = NULL;
            void* source_component = NULL;
            void** roots[] = {
                (void**) &target_root,
                (void**) &source_root,
                (void**) &target_component,
                (void**) &source_component
            };
            javan_root_frame_push(roots, 4);
            int result = 0;
            javan_runtime_class_state* target_state = javan_runtime_class_checked(target_root);
            javan_runtime_class_state* source_state = javan_runtime_class_checked(source_root);
            if (strcmp(target_state->binary_name, source_state->binary_name) == 0) {
                result = 1;
            } else if (target_state->exact_type_id == JAVAN_CLASS_EXACT_OBJECT) {
                result = javan_runtime_class_is_primitive_exact_type_id(source_state->exact_type_id) == 0 ? 1 : 0;
            } else if (source_state->exact_type_id != 0) {
                result = javan_runtime_class_accepts_type_id(target_state, source_state->exact_type_id);
            } else if (target_state->is_array != 0 && source_state->is_array != 0) {
                target_component = javan_runtime_class_component_type_from_binary_name(target_state->binary_name);
                source_component = javan_runtime_class_component_type_from_binary_name(source_state->binary_name);
                if (target_component != NULL && source_component != NULL) {
                    result = javan_class_is_assignable_from(target_component, source_component);
                }
            }
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_object_get_class(void* value) {
            if (value == NULL) {
                javan_panic("null object");
            }
            if (javan_is_system_class_loader(value) != 0) {
                return javan_runtime_class_literal("java.lang.ClassLoader", JAVAN_CLASS_EXACT_CLASS_LOADER, 0, 0, 0);
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node != NULL && node->kind == JAVAN_HEAP_KIND_ARRAY) {
                if (node->array_class_name == NULL || node->array_class_name[0] == '\\0') {
                    javan_panic("unsupported array class metadata");
                }
                return javan_runtime_class_literal(node->array_class_name, 0, 0, 1, 0);
            }
            int type_id = javan_registered_type_id(value);
            if (type_id > 0) {
                JavanTypeDescriptor* descriptor = javan_type_descriptor_for(type_id);
                if (descriptor != NULL && descriptor->name != NULL) {
                    return javan_runtime_class_literal(descriptor->name, type_id, descriptor->is_enum, 0, 1, type_id);
                }
                javan_panic("unsupported generated object type");
            }
            if (node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return javan_runtime_class_literal("java.lang.String", JAVAN_CLASS_EXACT_STRING, 0, 0, 0);
            }
            if (node == NULL && type_id == 0 && javan_probably_string_key(value) != 0) {
                return javan_runtime_class_literal("java.lang.String", JAVAN_CLASS_EXACT_STRING, 0, 0, 0);
            }
            if (node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS) {
                return javan_runtime_class_literal("java.lang.Class", JAVAN_CLASS_EXACT_CLASS, 0, 0, 0);
            }
            if (node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST) {
                return javan_runtime_class_literal("java.util.ArrayList", JAVAN_CLASS_EXACT_ARRAY_LIST, 0, 0, 0);
            }
            if (node != NULL && node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP) {
                return javan_runtime_class_literal("java.util.HashMap", JAVAN_CLASS_EXACT_HASH_MAP, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return javan_runtime_class_literal("java.lang.Integer", JAVAN_TYPE_JAVA_LANG_INTEGER, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return javan_runtime_class_literal("java.lang.Long", JAVAN_TYPE_JAVA_LANG_LONG, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_runtime_class_literal("java.lang.Float", JAVAN_TYPE_JAVA_LANG_FLOAT, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_runtime_class_literal("java.lang.Double", JAVAN_TYPE_JAVA_LANG_DOUBLE, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return javan_runtime_class_literal("java.lang.Boolean", JAVAN_TYPE_JAVA_LANG_BOOLEAN, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BYTE) {
                return javan_runtime_class_literal("java.lang.Byte", JAVAN_TYPE_JAVA_LANG_BYTE, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_SHORT) {
                return javan_runtime_class_literal("java.lang.Short", JAVAN_TYPE_JAVA_LANG_SHORT, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                return javan_runtime_class_literal("java.lang.Character", JAVAN_TYPE_JAVA_LANG_CHARACTER, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_THREAD) {
                return javan_runtime_class_literal("java.lang.Thread", JAVAN_TYPE_JAVA_LANG_THREAD, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL) {
                return javan_runtime_class_literal("java.lang.ThreadLocal", JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_INHERITABLE_THREAD_LOCAL) {
                return javan_runtime_class_literal("java.lang.InheritableThreadLocal", JAVAN_TYPE_JAVA_LANG_INHERITABLE_THREAD_LOCAL, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_TIME_DURATION) {
                return javan_runtime_class_literal("java.time.Duration", JAVAN_TYPE_JAVA_TIME_DURATION, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER) {
                return javan_runtime_class_literal("java.time.format.DateTimeFormatter", JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER) {
                return javan_runtime_class_literal("java.time.format.DateTimeFormatterBuilder", JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE) {
                return javan_runtime_class_literal("java.time.format.TextStyle", JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_UTIL_LOCALE) {
                return javan_runtime_class_literal("java.util.Locale", JAVAN_TYPE_JAVA_UTIL_LOCALE, 0, 0, 0);
            }
            if (type_id == JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME) {
                return javan_runtime_class_literal("java.nio.file.attribute.FileTime", JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME, 0, 0, 0);
            }
            javan_panic("unsupported getClass runtime object");
            return NULL;
        }

        void* javan_runtime_class_get_name(void* value) {
            void* class_root = value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            result = javan_string_from(javan_runtime_class_checked(class_root)->binary_name);
            javan_root_frame_pop(roots);
            return result;
        }

        static const char* javan_runtime_class_primitive_type_name(int exact_type_id) {
            switch (exact_type_id) {
                case JAVAN_CLASS_EXACT_PRIMITIVE_BOOLEAN:
                    return "boolean";
                case JAVAN_CLASS_EXACT_PRIMITIVE_BYTE:
                    return "byte";
                case JAVAN_CLASS_EXACT_PRIMITIVE_SHORT:
                    return "short";
                case JAVAN_CLASS_EXACT_PRIMITIVE_CHAR:
                    return "char";
                case JAVAN_CLASS_EXACT_PRIMITIVE_INT:
                    return "int";
                case JAVAN_CLASS_EXACT_PRIMITIVE_LONG:
                    return "long";
                case JAVAN_CLASS_EXACT_PRIMITIVE_FLOAT:
                    return "float";
                case JAVAN_CLASS_EXACT_PRIMITIVE_DOUBLE:
                    return "double";
                case JAVAN_CLASS_EXACT_PRIMITIVE_VOID:
                    return "void";
                default:
                    return NULL;
            }
        }

        void* javan_class_type_name(void* class_value) {
            void* class_root = class_value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_class_state* state = javan_runtime_class_checked(class_root);
            const char* primitive_name = javan_runtime_class_primitive_type_name(state->exact_type_id);
            if (primitive_name != NULL) {
                result = javan_string_from(primitive_name);
                javan_root_frame_pop(roots);
                return result;
            }
            if (state->is_array == 0) {
                result = javan_string_from(state->binary_name);
                javan_root_frame_pop(roots);
                return result;
            }
            const char* descriptor = state->binary_name;
            int dimensions = 0;
            while (descriptor[dimensions] == '[') {
                dimensions++;
            }
            const char* component = descriptor + dimensions;
            const char* base_name = NULL;
            char* owned_base_name = NULL;
            switch (component[0]) {
                case 'Z':
                    base_name = "boolean";
                    break;
                case 'B':
                    base_name = "byte";
                    break;
                case 'S':
                    base_name = "short";
                    break;
                case 'C':
                    base_name = "char";
                    break;
                case 'I':
                    base_name = "int";
                    break;
                case 'J':
                    base_name = "long";
                    break;
                case 'F':
                    base_name = "float";
                    break;
                case 'D':
                    base_name = "double";
                    break;
                case 'L': {
                    unsigned long component_length = (unsigned long) strlen(component);
                    if (component_length < 2UL || component[component_length - 1UL] != ';') {
                        javan_panic("unsupported array class metadata");
                    }
                    unsigned long base_length = component_length - 2UL;
                    owned_base_name = (char*) javan_raw_calloc_retry(base_length + 1UL);
                    if (owned_base_name == NULL) {
                        javan_panic("out of memory");
                    }
                    memcpy(owned_base_name, component + 1, base_length);
                    owned_base_name[base_length] = '\\0';
                    base_name = owned_base_name;
                    break;
                }
                default:
                    javan_panic("unsupported array class metadata");
            }
            unsigned long base_name_length = (unsigned long) strlen(base_name);
            unsigned long result_length = base_name_length + ((unsigned long) dimensions * 2UL);
            char* type_name = (char*) javan_raw_calloc_retry(result_length + 1UL);
            if (type_name == NULL) {
                free(owned_base_name);
                javan_panic("out of memory");
            }
            memcpy(type_name, base_name, base_name_length);
            for (int index = 0; index < dimensions; index++) {
                unsigned long offset = base_name_length + ((unsigned long) index * 2UL);
                type_name[offset] = '[';
                type_name[offset + 1UL] = ']';
            }
            type_name[result_length] = '\\0';
            result = javan_string_from(type_name);
            free(type_name);
            free(owned_base_name);
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_class_package_name(void* class_value) {
            void* class_root = class_value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_class_state* state = javan_runtime_class_checked(class_root);
            const char* binary_name = state->binary_name;
            if (state->is_array != 0) {
                int dimensions = 0;
                while (binary_name[dimensions] == '[') {
                    dimensions++;
                }
                const char* component = binary_name + dimensions;
                if (component[0] == 'L') {
                    unsigned long component_length = (unsigned long) strlen(component);
                    if (component_length < 2UL || component[component_length - 1UL] != ';') {
                        javan_panic("unsupported array class metadata");
                    }
                    char* component_binary_name = (char*) javan_raw_calloc_retry(component_length - 1UL);
                    if (component_binary_name == NULL) {
                        javan_panic("out of memory");
                    }
                    memcpy(component_binary_name, component + 1, component_length - 2UL);
                    component_binary_name[component_length - 2UL] = '\\0';
                    binary_name = component_binary_name;
                } else {
                    result = javan_string_from("java.lang");
                    javan_root_frame_pop(roots);
                    return result;
                }
            } else if (javan_runtime_class_is_primitive_exact_type_id(state->exact_type_id) != 0) {
                result = javan_string_from("java.lang");
                javan_root_frame_pop(roots);
                return result;
            }
            const char* package_end = strrchr(binary_name, '.');
            if (package_end == NULL) {
                if (binary_name != state->binary_name) {
                    free((void*) binary_name);
                }
                result = javan_string_from("");
                javan_root_frame_pop(roots);
                return result;
            }
            unsigned long package_length = (unsigned long) (package_end - binary_name);
            char* package_name = (char*) javan_raw_calloc_retry(package_length + 1UL);
            if (package_name == NULL) {
                if (binary_name != state->binary_name) {
                    free((void*) binary_name);
                }
                javan_panic("out of memory");
            }
            memcpy(package_name, binary_name, package_length);
            package_name[package_length] = '\\0';
            result = javan_string_from(package_name);
            free(package_name);
            if (binary_name != state->binary_name) {
                free((void*) binary_name);
            }
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_class_simple_name(void* class_value) {
            void* class_root = class_value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_class_state* state = javan_runtime_class_checked(class_root);
            const char* primitive_name = javan_runtime_class_primitive_type_name(state->exact_type_id);
            if (primitive_name != NULL) {
                result = javan_string_from(primitive_name);
                javan_root_frame_pop(roots);
                return result;
            }
            const char* binary_name = state->binary_name;
            int dimensions = 0;
            char* owned_base_name = NULL;
            if (state->is_array != 0) {
                while (binary_name[dimensions] == '[') {
                    dimensions++;
                }
                const char* component = binary_name + dimensions;
                switch (component[0]) {
                    case 'Z':
                        binary_name = "boolean";
                        break;
                    case 'B':
                        binary_name = "byte";
                        break;
                    case 'S':
                        binary_name = "short";
                        break;
                    case 'C':
                        binary_name = "char";
                        break;
                    case 'I':
                        binary_name = "int";
                        break;
                    case 'J':
                        binary_name = "long";
                        break;
                    case 'F':
                        binary_name = "float";
                        break;
                    case 'D':
                        binary_name = "double";
                        break;
                    case 'L': {
                        unsigned long component_length = (unsigned long) strlen(component);
                        if (component_length < 2UL || component[component_length - 1UL] != ';') {
                            javan_panic("unsupported array class metadata");
                        }
                        owned_base_name = (char*) javan_raw_calloc_retry(component_length - 1UL);
                        if (owned_base_name == NULL) {
                            javan_panic("out of memory");
                        }
                        memcpy(owned_base_name, component + 1, component_length - 2UL);
                        owned_base_name[component_length - 2UL] = '\\0';
                        binary_name = owned_base_name;
                        break;
                    }
                    default:
                        javan_panic("unsupported array class metadata");
                }
            }
            const char* simple_name = binary_name;
            const char* package_dot = strrchr(simple_name, '.');
            if (package_dot != NULL) {
                simple_name = package_dot + 1;
            }
            const char* member_dollar = strrchr(simple_name, '$');
            if (member_dollar != NULL) {
                simple_name = member_dollar + 1;
            }
            unsigned long simple_length = (unsigned long) strlen(simple_name);
            unsigned long result_length = simple_length + ((unsigned long) dimensions * 2UL);
            char* rendered = (char*) javan_raw_calloc_retry(result_length + 1UL);
            if (rendered == NULL) {
                free(owned_base_name);
                javan_panic("out of memory");
            }
            memcpy(rendered, simple_name, simple_length);
            for (int index = 0; index < dimensions; index++) {
                unsigned long offset = simple_length + ((unsigned long) index * 2UL);
                rendered[offset] = '[';
                rendered[offset + 1UL] = ']';
            }
            rendered[result_length] = '\\0';
            result = javan_string_from(rendered);
            free(rendered);
            free(owned_base_name);
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_class_descriptor_string(void* class_value) {
            void* class_root = class_value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_class_state* state = javan_runtime_class_checked(class_root);
            int primitive_descriptor = javan_runtime_class_primitive_descriptor_char(state->exact_type_id);
            if (primitive_descriptor != 0) {
                char descriptor[2];
                descriptor[0] = (char) primitive_descriptor;
                descriptor[1] = '\\0';
                result = javan_string_from(descriptor);
                javan_root_frame_pop(roots);
                return result;
            }
            unsigned long binary_length = (unsigned long) strlen(state->binary_name);
            if (state->is_array != 0) {
                char* descriptor = (char*) javan_raw_calloc_retry(binary_length + 1UL);
                if (descriptor == NULL) {
                    javan_panic("out of memory");
                }
                int in_object_component = 0;
                for (unsigned long index = 0; index < binary_length; index++) {
                    char ch = state->binary_name[index];
                    if (ch == 'L') {
                        in_object_component = 1;
                    } else if (ch == ';') {
                        in_object_component = 0;
                    } else if (in_object_component != 0 && ch == '.') {
                        ch = '/';
                    }
                    descriptor[index] = ch;
                }
                descriptor[binary_length] = '\\0';
                result = javan_string_from(descriptor);
                free(descriptor);
                javan_root_frame_pop(roots);
                return result;
            }
            char* descriptor = (char*) javan_raw_calloc_retry(binary_length + 3UL);
            if (descriptor == NULL) {
                javan_panic("out of memory");
            }
            descriptor[0] = 'L';
            for (unsigned long index = 0; index < binary_length; index++) {
                char ch = state->binary_name[index];
                descriptor[index + 1UL] = ch == '.' ? '/' : ch;
            }
            descriptor[binary_length + 1UL] = ';';
            descriptor[binary_length + 2UL] = '\\0';
            result = javan_string_from(descriptor);
            free(descriptor);
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_class_component_type(void* class_value) {
            void* class_root = class_value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_class_state* state = javan_runtime_class_checked(class_root);
            if (state->is_array == 0) {
                javan_root_frame_pop(roots);
                return NULL;
            }
            result = javan_runtime_class_component_type_from_binary_name(state->binary_name);
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_class_array_type(void* class_value) {
            void* class_root = class_value;
            void* result = NULL;
            void** roots[] = {
                (void**) &class_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_class_state* state = javan_runtime_class_checked(class_root);
            if (state->exact_type_id == JAVAN_CLASS_EXACT_PRIMITIVE_VOID) {
                javan_panic("Class.arrayType unsupported for void");
            }
            const char* primitive_array_binary_name = javan_runtime_class_primitive_array_binary_name(state->exact_type_id);
            if (primitive_array_binary_name != NULL) {
                result = javan_runtime_class_from_binary_name(primitive_array_binary_name);
                javan_root_frame_pop(roots);
                return result;
            }
            unsigned long binary_length = (unsigned long) strlen(state->binary_name);
            if (state->is_array != 0) {
                char* array_binary_name = (char*) javan_raw_calloc_retry(binary_length + 2UL);
                if (array_binary_name == NULL) {
                    javan_panic("out of memory");
                }
                array_binary_name[0] = '[';
                memcpy(array_binary_name + 1, state->binary_name, binary_length + 1UL);
                result = javan_runtime_class_from_binary_name(array_binary_name);
                free(array_binary_name);
                javan_root_frame_pop(roots);
                return result;
            }
            char* array_binary_name = (char*) javan_raw_calloc_retry(binary_length + 4UL);
            if (array_binary_name == NULL) {
                javan_panic("out of memory");
            }
            array_binary_name[0] = '[';
            array_binary_name[1] = 'L';
            memcpy(array_binary_name + 2, state->binary_name, binary_length);
            array_binary_name[binary_length + 2UL] = ';';
            array_binary_name[binary_length + 3UL] = '\\0';
            result = javan_runtime_class_from_binary_name(array_binary_name);
            free(array_binary_name);
            javan_root_frame_pop(roots);
            return result;
        }

        int javan_class_exact_type_id(void* class_value) {
            return javan_runtime_class_checked(class_value)->exact_type_id;
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_EXECUTOR = """
        static javan_object_list* javan_list_new_with_capacity(int capacity, int immutable);
        static javan_object_list* javan_list_new_view(javan_object_list* backing, int immutable, int view_flags);
        static javan_object_list* javan_list_checked(void* value);
        static void javan_list_append_raw(javan_object_list* list, void* value);
        int javan_hashset_add_all(void* value, void* collection);
        int javan_set_add(void* value, void* element);
        void* javan_virtual_thread_executor_from_factory(void* value);
        void javan_atomic_boolean_init(void* value, int initial_value);
        void javan_atomic_reference_init(void* value, void* initial_value);
        void javan_atomic_integer_init(void* value, int initial_value);
        void javan_atomic_long_init(void* value, long long initial_value);
        void javan_scheduled_thread_pool_executor_init(void* value, int core_pool_size);
        void javan_scheduled_thread_pool_executor_init_full(void* value, int core_pool_size, void* thread_factory, void* rejected_execution_handler);

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

        void* javan_scheduled_thread_pool_executor_new(void) {
            void* list_value = NULL;
            void* executor_value = NULL;
            void** roots[] = {
                (void**) &list_value,
                (void**) &executor_value
            };
            javan_root_frame_push(roots, 2);
            list_value = javan_list_new_with_capacity(0, 0);
            executor_value = javan_alloc(sizeof(javan_scheduled_thread_pool_executor_state));
            javan_scheduled_thread_pool_executor_state* state = (javan_scheduled_thread_pool_executor_state*) executor_value;
            state->magic = JAVAN_SCHEDULED_THREAD_POOL_EXECUTOR_MAGIC;
            state->core_pool_size = 0;
            state->closed = 0;
            state->reserved0 = 0;
            state->thread_factory = NULL;
            state->rejected_execution_handler = NULL;
            state->threads = (javan_object_list*) list_value;
            javan_update_runtime_allocation_kind(executor_value, JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR);
            javan_root_frame_pop(roots);
            return executor_value;
        }

        static javan_scheduled_thread_pool_executor_state* javan_scheduled_thread_pool_executor_state_for_init(void* value) {
            if (value == NULL) {
                javan_panic("unsupported scheduled thread pool executor");
            }
            struct javan_object_header* header = javan_generated_object_header(value);
            if (header == NULL) {
                return javan_scheduled_thread_pool_executor_checked(value);
            }
            if (header->_javan_runtime_state == NULL) {
                void* owner_root = value;
                void* state_value = NULL;
                void** roots[] = {
                    (void**) &owner_root,
                    (void**) &state_value
                };
                javan_root_frame_push(roots, 2);
                state_value = javan_scheduled_thread_pool_executor_new();
                javan_generated_object_attach_runtime_state(owner_root, state_value, JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR);
                javan_root_frame_pop(roots);
            }
            return javan_scheduled_thread_pool_executor_checked(value);
        }

        void* javan_atomic_long_new(void) {
            void* value = javan_alloc(sizeof(javan_atomic_long_state));
            javan_atomic_long_state* state = (javan_atomic_long_state*) value;
            state->magic = JAVAN_ATOMIC_LONG_MAGIC;
            state->reserved0 = 0;
            state->value = 0LL;
            javan_update_runtime_allocation_kind(value, JAVAN_RUNTIME_KIND_ATOMIC_LONG);
            return value;
        }

        void javan_scheduled_thread_pool_executor_init(void* value, int core_pool_size) {
            if (core_pool_size < 0) {
                javan_panic("negative scheduled thread pool core size");
            }
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_state_for_init(value);
            state->core_pool_size = core_pool_size;
            state->closed = 0;
            state->thread_factory = NULL;
            state->rejected_execution_handler = NULL;
        }

        void javan_scheduled_thread_pool_executor_init_full(
            void* value,
            int core_pool_size,
            void* thread_factory,
            void* rejected_execution_handler
        ) {
            if (core_pool_size < 0) {
                javan_panic("negative scheduled thread pool core size");
            }
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_state_for_init(value);
            state->core_pool_size = core_pool_size;
            state->closed = 0;
            state->thread_factory = thread_factory;
            state->rejected_execution_handler = rejected_execution_handler;
        }

        static long long javan_time_unit_to_nanos(void* unit, long long value);

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

        void* javan_virtual_thread_executor_submit(void* value, void* runnable) {
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
            return thread_value;
        }

        void javan_virtual_thread_executor_shutdown(void* value) {
            javan_virtual_thread_executor_checked(value)->closed = 1;
        }

        int javan_virtual_thread_executor_await_termination(void* value, long long timeout, void* unit) {
            javan_virtual_thread_executor_state* state = javan_virtual_thread_executor_checked(value);
            long long timeout_nanos = javan_time_unit_to_nanos(unit, timeout);
            long long deadline = javan_system_nano_time() + timeout_nanos;
            while (1) {
                int all_done = 1;
                if (state->threads != NULL && state->threads->values != NULL) {
                    for (int index = 0; index < state->threads->length; index++) {
                        void* thread_value = state->threads->values[index];
                        if (thread_value != NULL && javan_thread_is_alive(thread_value) != 0) {
                            all_done = 0;
                            break;
                        }
                    }
                }
                if (all_done != 0) {
                    return 1;
                }
                if (javan_system_nano_time() >= deadline) {
                    return 0;
                }
                javan_sleep_micros(5000UL);
            }
        }

        void* javan_virtual_thread_executor_shutdown_now(void* value) {
            void* executor_root = value;
            void* result = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_virtual_thread_executor_state* state = javan_virtual_thread_executor_checked(executor_root);
            state->closed = 1;
            result = javan_list_new_with_capacity(0, 0);
            if (state->threads != NULL && state->threads->values != NULL) {
                for (int index = 0; index < state->threads->length; index++) {
                    void* thread_value = state->threads->values[index];
                    if (thread_value != NULL && javan_thread_is_alive(thread_value) != 0) {
                        javan_thread_interrupt(thread_value);
                    }
                }
            }
            javan_root_frame_pop(roots);
            return result;
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

    private static final String SOURCE_HEAP_ALLOC_DATE_TIME = """
        static javan_datetime_formatter_state* javan_datetime_formatter_checked(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER) {
                javan_panic("unsupported date-time formatter");
            }
            javan_datetime_formatter_state* state = (javan_datetime_formatter_state*) value;
            if (state->magic != JAVAN_DATE_TIME_FORMATTER_MAGIC
                || state->builtin_kind < JAVAN_DATE_TIME_FORMATTER_ISO_ZONED_DATE_TIME
                || state->builtin_kind > JAVAN_DATE_TIME_FORMATTER_DATE_TO_STRING) {
                javan_panic("unsupported date-time formatter");
            }
            return state;
        }

        static javan_datetime_formatter_builder_state* javan_datetime_formatter_builder_checked(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER) {
                javan_panic("unsupported date-time formatter builder");
            }
            javan_datetime_formatter_builder_state* state = (javan_datetime_formatter_builder_state*) value;
            if (state->magic != JAVAN_DATE_TIME_FORMATTER_BUILDER_MAGIC
                || state->stage < JAVAN_DATE_TIME_BUILDER_STAGE_NEW
                || state->stage > JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_TAIL) {
                javan_panic("unsupported date-time formatter builder");
            }
            return state;
        }

        static javan_text_style_state* javan_text_style_checked(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE) {
                javan_panic("unsupported text style");
            }
            javan_text_style_state* state = (javan_text_style_state*) value;
            if (state->magic != JAVAN_TEXT_STYLE_MAGIC || state->style_kind != JAVAN_DATE_TIME_TEXT_STYLE_SHORT) {
                javan_panic("unsupported text style");
            }
            return state;
        }

        static javan_locale_state* javan_locale_checked(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_UTIL_LOCALE) {
                javan_panic("unsupported locale");
            }
            javan_locale_state* state = (javan_locale_state*) value;
            if (state->magic != JAVAN_LOCALE_MAGIC || state->locale_kind != JAVAN_DATE_TIME_LOCALE_ENGLISH) {
                javan_panic("unsupported locale");
            }
            return state;
        }

        void* javan_datetime_formatter_builtin(int kind) {
            if (kind < JAVAN_DATE_TIME_FORMATTER_ISO_ZONED_DATE_TIME || kind > JAVAN_DATE_TIME_FORMATTER_DATE_TO_STRING) {
                javan_panic("unsupported date-time formatter builtin");
            }
            javan_datetime_formatter_state* state = (javan_datetime_formatter_state*) javan_alloc(sizeof(javan_datetime_formatter_state));
            state->magic = JAVAN_DATE_TIME_FORMATTER_MAGIC;
            state->builtin_kind = kind;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_register_object((void*) state, JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER);
            return (void*) state;
        }

        void* javan_datetime_formatter_builder_new(void) {
            javan_datetime_formatter_builder_state* state = (javan_datetime_formatter_builder_state*) javan_alloc(sizeof(javan_datetime_formatter_builder_state));
            state->magic = JAVAN_DATE_TIME_FORMATTER_BUILDER_MAGIC;
            state->stage = JAVAN_DATE_TIME_BUILDER_STAGE_NEW;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_register_object((void*) state, JAVAN_TYPE_JAVA_TIME_FORMAT_DATE_TIME_FORMATTER_BUILDER);
            return (void*) state;
        }

        void* javan_text_style_short(void) {
            javan_text_style_state* state = (javan_text_style_state*) javan_alloc(sizeof(javan_text_style_state));
            state->magic = JAVAN_TEXT_STYLE_MAGIC;
            state->style_kind = JAVAN_DATE_TIME_TEXT_STYLE_SHORT;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_register_object((void*) state, JAVAN_TYPE_JAVA_TIME_FORMAT_TEXT_STYLE);
            return (void*) state;
        }

        void* javan_locale_english(void) {
            javan_locale_state* state = (javan_locale_state*) javan_alloc(sizeof(javan_locale_state));
            state->magic = JAVAN_LOCALE_MAGIC;
            state->locale_kind = JAVAN_DATE_TIME_LOCALE_ENGLISH;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_register_object((void*) state, JAVAN_TYPE_JAVA_UTIL_LOCALE);
            return (void*) state;
        }

        void* javan_datetime_formatter_builder_parse_case_insensitive(void* value) {
            javan_datetime_formatter_builder_state* state = javan_datetime_formatter_builder_checked(value);
            if (state->stage != JAVAN_DATE_TIME_BUILDER_STAGE_NEW) {
                javan_panic("unsupported date-time formatter builder flow");
            }
            state->stage = JAVAN_DATE_TIME_BUILDER_STAGE_CASE_INSENSITIVE;
            return value;
        }

        void* javan_datetime_formatter_builder_append_pattern(void* value, void* pattern) {
            javan_datetime_formatter_builder_state* state = javan_datetime_formatter_builder_checked(value);
            const char* text = (const char*) javan_printable_object_string(pattern);
            if (text == NULL) {
                javan_panic("unsupported date-time formatter pattern");
            }
            if (state->stage == JAVAN_DATE_TIME_BUILDER_STAGE_CASE_INSENSITIVE
                && strcmp(text, "EEE MMM dd HH:mm:ss") == 0) {
                state->stage = JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_HEAD;
                return value;
            }
            if (state->stage == JAVAN_DATE_TIME_BUILDER_STAGE_ZONE_TEXT
                && strcmp(text, " yyyy") == 0) {
                state->stage = JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_TAIL;
                return value;
            }
            javan_panic("unsupported date-time formatter pattern");
            return NULL;
        }

        void* javan_datetime_formatter_builder_append_zone_text(void* value, void* style) {
            javan_datetime_formatter_builder_state* state = javan_datetime_formatter_builder_checked(value);
            javan_text_style_checked(style);
            if (state->stage != JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_HEAD) {
                javan_panic("unsupported date-time formatter builder flow");
            }
            state->stage = JAVAN_DATE_TIME_BUILDER_STAGE_ZONE_TEXT;
            return value;
        }

        void* javan_datetime_formatter_builder_to_formatter(void* value, void* locale) {
            javan_datetime_formatter_builder_state* state = javan_datetime_formatter_builder_checked(value);
            javan_locale_checked(locale);
            if (state->stage != JAVAN_DATE_TIME_BUILDER_STAGE_PATTERN_TAIL) {
                javan_panic("unsupported date-time formatter builder flow");
            }
            return javan_datetime_formatter_builtin(JAVAN_DATE_TIME_FORMATTER_DATE_TO_STRING);
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_TAIL = """
        void* javan_thread_new(void) {
            javan_thread* object = (javan_thread*) javan_alloc(sizeof(javan_thread));
            object->interrupted = 0;
            object->started = 0;
            object->completed = 0;
            object->future_cancelled = 0;
            object->virtual_thread = 0;
            object->inherit_inheritable_thread_locals = 1;
            object->daemon = 0;
            object->priority = 5;
            object->park_permit = 0;
            object->schedule_mode = 0;
            object->scheduled_first_run_started = 0;
            object->id = javan_thread_next_id();
            object->name = NULL;
            object->scheduled_initial_delay_nanos = 0LL;
            object->scheduled_period_nanos = 0LL;
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
            object->scheduled_executor = NULL;
            object->thread_locals = NULL;
            if (javan_current_thread_value != NULL) {
                object->priority = ((javan_thread*) javan_current_thread_value)->priority;
            }
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

        static void* javan_thread_local_new_with_inheritance(int inheritable) {
            javan_thread_local* object = (javan_thread_local*) javan_alloc(sizeof(javan_thread_local));
            object->inheritable = inheritable == 0 ? 0 : 1;
            javan_register_object(
                (void*) object,
                inheritable == 0 ? JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL : JAVAN_TYPE_JAVA_LANG_INHERITABLE_THREAD_LOCAL
            );
            return object;
        }

        void* javan_thread_local_new(void) {
            return javan_thread_local_new_with_inheritance(0);
        }

        void* javan_inheritable_thread_local_new(void) {
            return javan_thread_local_new_with_inheritance(1);
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

        int javan_float_to_raw_int_bits(float value) {
            uint32_t bits;
            int result;
            memcpy(&bits, &value, sizeof(bits));
            memcpy(&result, &bits, sizeof(result));
            return result;
        }

        int javan_float_is_finite(float value) {
            const unsigned int bits = (unsigned int) javan_float_to_raw_int_bits(value);
            return (bits & 0x7f800000U) != 0x7f800000U;
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

        void* javan_byte_value_of(int value) {
            javan_boxed_byte* object = (javan_boxed_byte*) javan_alloc(sizeof(javan_boxed_byte));
            object->value = (signed char) value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_BYTE);
            return object;
        }

        int javan_byte_byte_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_BYTE) {
                javan_panic("not a Byte");
            }
            return ((javan_boxed_byte*) value)->value;
        }

        void* javan_short_value_of(int value) {
            javan_boxed_short* object = (javan_boxed_short*) javan_alloc(sizeof(javan_boxed_short));
            object->value = (short) value;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_SHORT);
            return object;
        }

        int javan_short_short_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_SHORT) {
                javan_panic("not a Short");
            }
            return ((javan_boxed_short*) value)->value;
        }

        void* javan_character_value_of(int value) {
            javan_boxed_character* object = (javan_boxed_character*) javan_alloc(sizeof(javan_boxed_character));
            object->value = value & 0xFFFF;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_CHARACTER);
            return object;
        }

        int javan_character_char_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                javan_panic("not a Character");
            }
            return ((javan_boxed_character*) value)->value;
        }

        static int javan_float_to_int(float value) {
            if (value != value) {
                return 0;
            }
            if (value >= 2147483647.0f) {
                return 2147483647;
            }
            if (value <= -2147483648.0f) {
                return (-2147483647 - 1);
            }
            return (int) value;
        }

        static int javan_double_to_int(double value) {
            if (value != value) {
                return 0;
            }
            if (value >= 2147483647.0) {
                return 2147483647;
            }
            if (value <= -2147483648.0) {
                return (-2147483647 - 1);
            }
            return (int) value;
        }

        int javan_is_supported_number(void* value) {
            int type_id = javan_registered_type_id(value);
            return type_id == JAVAN_TYPE_JAVA_LANG_INTEGER
                || type_id == JAVAN_TYPE_JAVA_LANG_LONG
                || type_id == JAVAN_TYPE_JAVA_LANG_FLOAT
                || type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE
                || type_id == JAVAN_TYPE_JAVA_LANG_BYTE
                || type_id == JAVAN_TYPE_JAVA_LANG_SHORT;
        }

        int javan_number_int_value(void* value) {
            int type_id = javan_registered_type_id(value);
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return javan_integer_int_value(value);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return javan_l2i(javan_long_long_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_float_to_int(javan_float_float_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_double_to_int(javan_double_double_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BYTE) {
                return javan_byte_byte_value(value);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_SHORT) {
                return javan_short_short_value(value);
            }
            javan_panic("not a supported Number");
            return 0;
        }

        int javan_boolean_equals(void* left, void* right) {
            if (right == NULL) {
                return 0;
            }
            if (javan_registered_type_id(left) != JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                javan_panic("not a Boolean");
            }
            if (javan_registered_type_id(right) != JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return 0;
            }
            return ((javan_boxed_boolean*) left)->value == ((javan_boxed_boolean*) right)->value ? 1 : 0;
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

        void* javan_caller_runs_policy_new(void) {
            unsigned char* value = (unsigned char*) javan_alloc(sizeof(unsigned char));
            value[0] = 0;
            return (void*) value;
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
            thread->future_cancelled = 0;
        }

        static void javan_thread_mark_completed(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            thread->completed = 1;
            thread->park_permit = 0;
            thread->target = NULL;
            thread->scheduled_first_run_started = 0;
            thread->scheduled_executor = NULL;
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

        static int javan_thread_completion_is_signaled(javan_thread* thread) {
            if (thread == NULL) {
                javan_panic("invalid Thread state");
            }
            #if defined(_WIN32)
            AcquireSRWLockShared(&thread->native_completion_lock);
            int signaled = thread->native_completion_signaled != 0;
            ReleaseSRWLockShared(&thread->native_completion_lock);
            return signaled;
            #else
            if (thread->native_sync_initialized == 0) {
                javan_panic("invalid Thread completion state");
            }
            if (pthread_mutex_lock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to acquire thread completion mutex");
            }
            int signaled = thread->native_completion_signaled != 0;
            if (pthread_mutex_unlock(&thread->native_completion_mutex) != 0) {
                javan_panic("unable to release thread completion mutex");
            }
            return signaled;
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
            thread->future_cancelled = 0;
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
            javan_objects_require_non_null_msg(name, "null Thread name");
            thread->name = (char*) name;
        }

        void javan_thread_set_name_nullable(void* value, void* name) {
            if (name == NULL) {
                return;
            }
            javan_thread_set_name(value, name);
        }

        void javan_thread_set_daemon(void* value, int daemon) {
            javan_require_thread(value)->daemon = daemon != 0 ? 1 : 0;
        }

        int javan_thread_is_daemon(void* value) {
            return javan_require_thread(value)->daemon != 0;
        }

        void javan_thread_set_priority(void* value, int priority) {
            if (priority < 1 || priority > 10) {
                javan_panic("invalid Thread priority");
            }
            javan_require_thread(value)->priority = priority;
        }

        int javan_thread_get_priority(void* value) {
            return javan_require_thread(value)->priority;
        }

        long long javan_thread_get_id(void* value) {
            return javan_require_thread(value)->id;
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
            javan_root_frame_cache_cleanup();
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
            const int type_id = javan_registered_type_id(value);
            if (type_id != JAVAN_TYPE_JAVA_LANG_THREAD_LOCAL
                && type_id != JAVAN_TYPE_JAVA_LANG_INHERITABLE_THREAD_LOCAL) {
                javan_panic("unsupported ThreadLocal object");
            }
            return (javan_thread_local*) value;
        }

        static int javan_thread_local_is_inheritable(void* value) {
            return javan_require_thread_local(value)->inheritable != 0;
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

        static void javan_thread_local_inherit_storage(javan_thread* parent, javan_thread* child) {
            if (parent == NULL || child == NULL || parent->thread_locals == NULL) {
                return;
            }
            javan_object_map* parent_storage = javan_map_checked(parent->thread_locals);
            int inheritable_entries = 0;
            for (int index = 0; index < parent_storage->length; index++) {
                if (javan_thread_local_is_inheritable(parent_storage->keys[index])) {
                    inheritable_entries++;
                }
            }
            if (inheritable_entries == 0) {
                return;
            }
            javan_object_map* child_storage = javan_thread_local_storage(child);
            for (int index = 0; index < parent_storage->length; index++) {
                if (!javan_thread_local_is_inheritable(parent_storage->keys[index])) {
                    continue;
                }
                void* key_root = parent_storage->keys[index];
                void* value_root = parent_storage->values[index];
                void** javan_thread_local_inherit_roots[] = {
                    (void**) &child_storage,
                    (void**) &key_root,
                    (void**) &value_root
                };
                javan_root_frame_push(javan_thread_local_inherit_roots, 3);
                javan_map_ensure_capacity(child_storage, child_storage->length + 1);
                child_storage->keys[child_storage->length] = key_root;
                child_storage->values[child_storage->length] = value_root;
                child_storage->length++;
                child_storage->mod_count++;
                javan_root_frame_pop(javan_thread_local_inherit_roots);
            }
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

        static long long javan_time_unit_to_nanos(void* unit, long long value) {
            const char* name = (const char*) unit;
            if (name == NULL) {
                javan_panic("unsupported TimeUnit");
            }
            if (value < 0LL) {
                javan_panic("negative schedule duration");
            }
            if (javan_string_equals(name, "NANOSECONDS")) {
                return value;
            }
            if (javan_string_equals(name, "MICROSECONDS")) {
                return value * 1000LL;
            }
            if (javan_string_equals(name, "MILLISECONDS")) {
                return value * 1000000LL;
            }
            if (javan_string_equals(name, "SECONDS")) {
                return value * 1000000000LL;
            }
            if (javan_string_equals(name, "MINUTES")) {
                return value * 60LL * 1000000000LL;
            }
            if (javan_string_equals(name, "HOURS")) {
                return value * 3600LL * 1000000000LL;
            }
            if (javan_string_equals(name, "DAYS")) {
                return value * 86400LL * 1000000000LL;
            }
            javan_panic("unsupported TimeUnit");
            return 0LL;
        }

        static int javan_thread_scheduler_closed(javan_thread* thread) {
            if (thread == NULL
                || (thread->schedule_mode != 2 && thread->schedule_mode != 3)
                || thread->scheduled_executor == NULL) {
                return 0;
            }
            return javan_scheduled_thread_pool_executor_checked(thread->scheduled_executor)->closed != 0;
        }

        static int javan_thread_sleep_nanos_interruptible(javan_thread* thread, long long nanos) {
            if (nanos <= 0LL) {
                return 0;
            }
            long long started = javan_system_nano_time();
            while (1) {
                if (javan_thread_current_interrupted_peek() != 0) {
                    (void) javan_thread_interrupted();
                    return 1;
                }
                if (thread != NULL && thread->future_cancelled != 0) {
                    return 2;
                }
                if (javan_thread_scheduler_closed(thread) != 0) {
                    return 2;
                }
                long long elapsed = javan_system_nano_time() - started;
                if (elapsed >= nanos) {
                    return 0;
                }
                long long remaining = nanos - elapsed;
                long long chunk_nanos = remaining > 5000000LL ? 5000000LL : remaining;
                if (chunk_nanos <= 0LL) {
                    return 0;
                }
                javan_sleep_micros((unsigned long) ((chunk_nanos + 999LL) / 1000LL));
            }
        }

        static void javan_thread_run_registered_target(void* value) {
            javan_thread* thread = javan_require_thread(value);
            void* target = thread->target;
            void** javan_thread_start_roots[] = { &value, &target };
            javan_root_frame_push(javan_thread_start_roots, 2);
            if (thread->schedule_mode == 0) {
                if (thread->future_cancelled == 0 && target != NULL) {
                    javan_thread_run_target(target);
                }
            } else {
                long long next_fire = javan_system_nano_time() + thread->scheduled_initial_delay_nanos;
                if (thread->scheduled_initial_delay_nanos > 0LL
                    && javan_thread_sleep_nanos_interruptible(thread, thread->scheduled_initial_delay_nanos) != 0) {
                    if (javan_root_frames_value != NULL && javan_root_frames_value->roots == javan_thread_start_roots) {
                        javan_root_frame_pop(javan_thread_start_roots);
                    }
                    return;
                }
                while (1) {
                    if (thread->future_cancelled != 0) {
                        break;
                    }
                    if (javan_thread_scheduler_closed(thread) != 0) {
                        break;
                    }
                    if (target != NULL) {
                        thread->scheduled_first_run_started = 1;
                        javan_thread_run_target(target);
                    }
                    if ((thread->schedule_mode != 2 && thread->schedule_mode != 3) || thread->scheduled_period_nanos <= 0LL) {
                        break;
                    }
                    if (javan_thread_current_interrupted_peek() != 0) {
                        (void) javan_thread_interrupted();
                        break;
                    }
                    long long remaining;
                    if (thread->schedule_mode == 2) {
                        next_fire += thread->scheduled_period_nanos;
                        remaining = next_fire - javan_system_nano_time();
                    } else {
                        remaining = thread->scheduled_period_nanos;
                    }
                    if (remaining > 0LL && javan_thread_sleep_nanos_interruptible(thread, remaining) != 0) {
                        break;
                    }
                }
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
            javan_root_frame_cache_cleanup();
            javan_runtime_lock_enter();
            javan_thread_leave_live_root(value);
            #if defined(_WIN32)
            if (thread->native_handle != NULL) {
                CloseHandle((HANDLE) thread->native_handle);
                thread->native_handle = NULL;
            }
            #endif
            javan_thread_completion_signal(thread);
            javan_current_thread_value = NULL;
            javan_runtime_lock_leave();
            #if defined(_WIN32)
            return 0U;
            #else
            return NULL;
            #endif
        }

        static void javan_thread_wait_for_completion(javan_thread* thread) {
            javan_runtime_lock_enter();
            int started = thread->started != 0;
            javan_runtime_lock_leave();
            if (started == 0) {
                return;
            }
            #if defined(_WIN32)
            AcquireSRWLockExclusive(&thread->native_completion_lock);
            while (thread->native_completion_signaled == 0) {
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
            while (thread->native_completion_signaled == 0) {
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

        int javan_thread_sleep_millis_nanos_interruptible(long long millis, int nanos) {
            if (millis < 0) {
                javan_panic("negative Thread.sleep millis");
            }
            if (nanos < 0 || nanos > 999999) {
                javan_panic("invalid Thread.sleep nanos");
            }
            long long total_nanos = (millis * 1000000LL) + (long long) nanos;
            if (total_nanos <= 0LL) {
                return 0;
            }
            return javan_thread_sleep_nanos_interruptible(javan_current_thread_object(), total_nanos);
        }

        void javan_thread_yield(void) {
            javan_os_thread_yield();
        }

        void javan_thread_on_spin_wait(void) {
            javan_cpu_spin_wait_hint();
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

        int javan_future_cancel(void* value, int may_interrupt_if_running) {
            javan_thread* thread = javan_require_thread(value);
            javan_runtime_lock_enter();
            int already_done = thread->completed != 0 || thread->future_cancelled != 0;
            if (already_done != 0) {
                javan_runtime_lock_leave();
                return 0;
            }
            thread->future_cancelled = 1;
            if (may_interrupt_if_running != 0) {
                thread->interrupted = 1;
                javan_profile_thread_interrupt_calls_value++;
            }
            javan_runtime_lock_leave();
            return 1;
        }

        int javan_future_is_done(void* value) {
            javan_thread* thread = javan_require_thread(value);
            javan_runtime_lock_enter();
            int done = thread->completed != 0 || thread->future_cancelled != 0;
            javan_runtime_lock_leave();
            return done;
        }

        int javan_future_is_cancelled(void* value) {
            javan_thread* thread = javan_require_thread(value);
            javan_runtime_lock_enter();
            int cancelled = thread->future_cancelled != 0;
            javan_runtime_lock_leave();
            return cancelled;
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
            javan_thread* parent = javan_current_thread_object();
            javan_thread_enter_live_root(value);
            javan_runtime_lock_enter();
            javan_profile_thread_start_calls_value++;
            if (thread->inherit_inheritable_thread_locals != 0) {
                javan_thread_local_inherit_storage(parent, thread);
            }
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

        static void javan_thread_set_scheduled_task(
            void* value,
            void* scheduled_executor,
            int schedule_mode,
            long long initial_delay_nanos,
            long long period_nanos
        ) {
            javan_thread* thread = javan_require_thread(value);
            thread->scheduled_executor = scheduled_executor;
            thread->schedule_mode = schedule_mode;
            thread->scheduled_first_run_started = 0;
            thread->scheduled_initial_delay_nanos = initial_delay_nanos;
            thread->scheduled_period_nanos = period_nanos;
        }

        void* javan_scheduled_thread_pool_executor_schedule(void* value, void* runnable, long long delay, void* unit) {
            void* executor_root = value;
            void* runnable_root = runnable;
            void* unit_root = unit;
            void* thread_value = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &runnable_root,
                (void**) &unit_root,
                (void**) &thread_value
            };
            javan_root_frame_push(roots, 4);
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_checked(executor_root);
            if (state->closed != 0) {
                javan_panic("scheduled thread pool executor is closed");
            }
            if (state->thread_factory != NULL) {
                thread_value = javan_virtual_thread_factory_new_thread(state->thread_factory, runnable_root);
            } else {
                thread_value = javan_thread_new_virtual();
                javan_thread_set_target(thread_value, runnable_root);
            }
            javan_thread_set_scheduled_task(thread_value, executor_root, 1, javan_time_unit_to_nanos(unit_root, delay), 0LL);
            javan_thread_start(thread_value);
            javan_list_append_raw(state->threads, thread_value);
            javan_root_frame_pop(roots);
            return thread_value;
        }

        void* javan_scheduled_thread_pool_executor_schedule_at_fixed_rate(
            void* value,
            void* runnable,
            long long initial_delay,
            long long period,
            void* unit
        ) {
            void* executor_root = value;
            void* runnable_root = runnable;
            void* unit_root = unit;
            void* thread_value = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &runnable_root,
                (void**) &unit_root,
                (void**) &thread_value
            };
            javan_root_frame_push(roots, 4);
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_checked(executor_root);
            if (state->closed != 0) {
                javan_panic("scheduled thread pool executor is closed");
            }
            if (period <= 0LL) {
                javan_panic("non-positive scheduleAtFixedRate period");
            }
            if (state->thread_factory != NULL) {
                thread_value = javan_virtual_thread_factory_new_thread(state->thread_factory, runnable_root);
            } else {
                thread_value = javan_thread_new_virtual();
                javan_thread_set_target(thread_value, runnable_root);
            }
            javan_thread_set_scheduled_task(
                thread_value,
                executor_root,
                2,
                javan_time_unit_to_nanos(unit_root, initial_delay),
                javan_time_unit_to_nanos(unit_root, period)
            );
            javan_thread_start(thread_value);
            javan_list_append_raw(state->threads, thread_value);
            javan_root_frame_pop(roots);
            return thread_value;
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_SCHEDULE_FIXED_DELAY = """
        void* javan_scheduled_thread_pool_executor_schedule_with_fixed_delay(
            void* value,
            void* runnable,
            long long initial_delay,
            long long delay,
            void* unit
        ) {
            void* executor_root = value;
            void* runnable_root = runnable;
            void* unit_root = unit;
            void* thread_value = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &runnable_root,
                (void**) &unit_root,
                (void**) &thread_value
            };
            javan_root_frame_push(roots, 4);
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_checked(executor_root);
            if (state->closed != 0) {
                javan_panic("scheduled thread pool executor is closed");
            }
            if (delay <= 0LL) {
                javan_panic("non-positive scheduleWithFixedDelay delay");
            }
            if (state->thread_factory != NULL) {
                thread_value = javan_virtual_thread_factory_new_thread(state->thread_factory, runnable_root);
            } else {
                thread_value = javan_thread_new_virtual();
                javan_thread_set_target(thread_value, runnable_root);
            }
            javan_thread_set_scheduled_task(
                thread_value,
                executor_root,
                3,
                javan_time_unit_to_nanos(unit_root, initial_delay),
                javan_time_unit_to_nanos(unit_root, delay)
            );
            javan_thread_start(thread_value);
            javan_list_append_raw(state->threads, thread_value);
            javan_root_frame_pop(roots);
            return thread_value;
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_TAIL_CONTINUED = """
        void javan_scheduled_thread_pool_executor_shutdown(void* value) {
            javan_scheduled_thread_pool_executor_checked(value)->closed = 1;
        }

        int javan_scheduled_thread_pool_executor_await_termination(void* value, long long timeout, void* unit) {
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_checked(value);
            long long timeout_nanos = javan_time_unit_to_nanos(unit, timeout);
            long long deadline = javan_system_nano_time() + timeout_nanos;
            while (1) {
                int all_done = 1;
                if (state->threads != NULL && state->threads->values != NULL) {
                    for (int index = 0; index < state->threads->length; index++) {
                        void* thread_value = state->threads->values[index];
                        if (thread_value != NULL && javan_thread_is_alive(thread_value) != 0) {
                            all_done = 0;
                            break;
                        }
                    }
                }
                if (all_done != 0) {
                    return 1;
                }
                if (javan_system_nano_time() >= deadline) {
                    return 0;
                }
                javan_sleep_micros(5000UL);
            }
        }

        void* javan_scheduled_thread_pool_executor_shutdown_now(void* value) {
            void* executor_root = value;
            void* result = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &result
            };
            javan_root_frame_push(roots, 2);
            javan_scheduled_thread_pool_executor_state* state = javan_scheduled_thread_pool_executor_checked(executor_root);
            state->closed = 1;
            result = javan_list_new_with_capacity(0, 0);
            if (state->threads != NULL && state->threads->values != NULL) {
                for (int index = 0; index < state->threads->length; index++) {
                    void* thread_value = state->threads->values[index];
                    if (thread_value != NULL && javan_thread_is_alive(thread_value) != 0) {
                        javan_thread* thread = (javan_thread*) thread_value;
                        if (thread->schedule_mode != 0
                            && thread->scheduled_first_run_started == 0
                            && thread->target != NULL) {
                            javan_list_append_raw((javan_object_list*) result, thread->target);
                        }
                        javan_thread_interrupt(thread_value);
                    }
                }
            }
            javan_root_frame_pop(roots);
            return result;
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
                javan_runtime_lock_enter();
                int not_started = thread->started == 0;
                javan_runtime_lock_leave();
                int done = not_started != 0 || javan_thread_completion_is_signaled(thread) != 0;
                if (done != 0) {
                    return 0;
                }
                if (javan_thread_current_interrupted_peek() != 0) {
                    (void) javan_thread_interrupted();
                    javan_runtime_lock_enter();
                    javan_profile_thread_join_interruptions_value++;
                    javan_runtime_lock_leave();
                    return 1;
                }
                javan_sleep_micros(5000UL);
            }
        }

        int javan_thread_join_millis_nanos_interruptible(void* value, long long millis, int nanos) {
            if (millis < 0) {
                javan_panic("negative Thread.join millis");
            }
            if (nanos < 0 || nanos > 999999) {
                javan_panic("invalid Thread.join nanos");
            }
            if (millis == 0LL && nanos == 0) {
                return javan_thread_join_interruptible(value);
            }
            javan_thread* thread = javan_require_thread(value);
            javan_thread* current = javan_current_thread_object();
            if (thread == current) {
                javan_panic("Thread.join on current thread is not supported yet");
            }
            long long total_nanos = (millis * 1000000LL) + (long long) nanos;
            javan_runtime_lock_enter();
            javan_profile_thread_join_calls_value++;
            javan_runtime_lock_leave();
            long long started = javan_system_nano_time();
            while (1) {
                javan_runtime_lock_enter();
                int not_started = thread->started == 0;
                javan_runtime_lock_leave();
                int done = not_started != 0 || javan_thread_completion_is_signaled(thread) != 0;
                if (done != 0) {
                    return 0;
                }
                if (javan_thread_current_interrupted_peek() != 0) {
                    (void) javan_thread_interrupted();
                    javan_runtime_lock_enter();
                    javan_profile_thread_join_interruptions_value++;
                    javan_runtime_lock_leave();
                    return 1;
                }
                long long elapsed = javan_system_nano_time() - started;
                if (elapsed >= total_nanos) {
                    return 0;
                }
                long long remaining = total_nanos - elapsed;
                long long chunk_nanos = remaining > 5000000LL ? 5000000LL : remaining;
                if (chunk_nanos <= 0LL) {
                    return 0;
                }
                javan_sleep_micros((unsigned long) ((chunk_nanos + 999LL) / 1000LL));
            }
        }

        int javan_thread_join_millis_interruptible(void* value, long long millis) {
            if (millis < 0) {
                javan_panic("negative Thread.join millis");
            }
            if (millis == 0LL) {
                return javan_thread_join_interruptible(value);
            }
            return javan_thread_join_millis_nanos_interruptible(value, millis, 0);
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
            void* next = NULL;
            void** javan_wait_for_non_current_threads_roots[] = { &next };
            javan_root_frame_push(javan_wait_for_non_current_threads_roots, 1);
            while (1) {
                javan_runtime_lock_enter();
                next = NULL;
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
                    break;
                }
                javan_thread_join(next);
            }
            javan_runtime_lock_enter();
            next = NULL;
            javan_runtime_lock_leave();
            javan_root_frame_pop(javan_wait_for_non_current_threads_roots);
        }

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
        } javan_array_header;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            void* values[];
        } javan_object_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            int values[];
        } javan_int_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            long long values[];
        } javan_long_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            float values[];
        } javan_float_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            double values[];
        } javan_double_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            signed char values[];
        } javan_byte_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
            short values[];
        } javan_short_array;

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
            const char* class_name;
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
            javan_gc_mark_value((void*) list->backing);
            javan_gc_mark_value((void*) list->values);
            for (int index = 0; index < list->length; index++) {
                javan_gc_mark_value(list->values[index]);
            }
        }

        static void javan_gc_mark_runtime_map(javan_object_map* map) {
            if (map == NULL || map->magic != JAVAN_OBJECT_MAP_MAGIC) {
                return;
            }
            javan_gc_mark_value((void*) map->backing);
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA) {
                javan_materialized_lambda_state* state =
                    javan_materialized_lambda_wrapper_state_unlocked(value);
                if (state != NULL) {
                    struct javan_object_header* header = (struct javan_object_header*) value;
                    javan_gc_mark_value(header->_javan_runtime_state);
                } else {
                    state = javan_materialized_lambda_state_node_unlocked(value);
                    if (state == NULL) {
                        javan_panic("invalid materialized lambda metadata");
                    }
                    javan_gc_mark_value((void*) state->captures);
                    for (int index = 0; index < state->capture_count; index++) {
                        javan_gc_mark_value(state->captures[index]);
                    }
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_SCHEDULED_THREAD_POOL_EXECUTOR) {
                javan_scheduled_thread_pool_executor_state* state = (javan_scheduled_thread_pool_executor_state*) value;
                if (state != NULL && state->magic == JAVAN_SCHEDULED_THREAD_POOL_EXECUTOR_MAGIC) {
                    javan_gc_mark_value(state->thread_factory);
                    javan_gc_mark_value(state->rejected_execution_handler);
                    javan_gc_mark_value((void*) state->threads);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE) {
                javan_atomic_reference_state* state = (javan_atomic_reference_state*) value;
                if (state != NULL && state->magic == JAVAN_ATOMIC_REFERENCE_MAGIC) {
                    javan_gc_mark_value(state->value);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_INET_ADDRESS) {
                javan_inet_address* address = (javan_inet_address*) value;
                if (address != NULL && address->magic == JAVAN_INET_ADDRESS_MAGIC) {
                    javan_gc_mark_value((void*) address->host_address);
                    javan_gc_mark_value((void*) address->host_name);
                    javan_gc_mark_value((void*) address->canonical_host_name);
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_RESOURCE_INPUT_STREAM) {
                javan_resource_input_stream_value* stream = (javan_resource_input_stream_value*) value;
                if (stream != NULL && stream->magic == JAVAN_RESOURCE_INPUT_STREAM_MAGIC) {
                    javan_gc_mark_value(stream->bytes);
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
                if (node->type_id > 0) {
                    struct javan_object_header* header = (struct javan_object_header*) value;
                    javan_gc_mark_value(header->_javan_runtime_state);
                }
                if (node->type_id == JAVAN_TYPE_JAVA_LANG_THREAD) {
                    javan_gc_mark_value(((javan_thread*) value)->name);
                    javan_gc_mark_value(((javan_thread*) value)->target);
                    javan_gc_mark_value(((javan_thread*) value)->scheduled_executor);
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
            javan_gc_mark_object_handles();
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
                    javan_allocation_cache_remove(node->value);
                    javan_allocation_registry_remove(node->value);
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
        static void javan_array_init(javan_array_header* array, int length, int element_size, int kind, const char* class_name) {
            array->length = length;
            array->element_size = element_size;
            array->kind = kind;
            array->reserved = 0;
            array->class_name = class_name;
            javan_update_allocation_metadata((void*) array, JAVAN_HEAP_KIND_ARRAY, kind);
            javan_update_array_class_name((void*) array, class_name);
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

        void* javan_object_array_new(int length, const char* class_name) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_object_array), length, sizeof(void*));
            javan_object_array* array = (javan_object_array*) javan_alloc(size);
            if (class_name == NULL || class_name[0] == '\\0') {
                javan_panic("invalid object array class name");
            }
            javan_array_init((javan_array_header*) array, length, sizeof(void*), JAVAN_ARRAY_KIND_OBJECT, class_name);
            return array;
        }

        void* javan_int_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_int_array), length, sizeof(int));
            javan_int_array* array = (javan_int_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(int), JAVAN_ARRAY_KIND_INT, "[I");
            return array;
        }

        void* javan_long_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_long_array), length, sizeof(long long));
            javan_long_array* array = (javan_long_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(long long), JAVAN_ARRAY_KIND_LONG, "[J");
            return array;
        }

        void* javan_float_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_float_array), length, sizeof(float));
            javan_float_array* array = (javan_float_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(float), JAVAN_ARRAY_KIND_FLOAT, "[F");
            return array;
        }

        void* javan_double_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_double_array), length, sizeof(double));
            javan_double_array* array = (javan_double_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(double), JAVAN_ARRAY_KIND_DOUBLE, "[D");
            return array;
        }

        void* javan_byte_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_byte_array), length, sizeof(signed char));
            javan_byte_array* array = (javan_byte_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(signed char), JAVAN_ARRAY_KIND_BYTE, "[B");
            return array;
        }

        void* javan_boolean_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_byte_array), length, sizeof(signed char));
            javan_byte_array* array = (javan_byte_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(signed char), JAVAN_ARRAY_KIND_BOOLEAN, "[Z");
            return array;
        }

        void* javan_short_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_short_array), length, sizeof(short));
            javan_short_array* array = (javan_short_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(short), JAVAN_ARRAY_KIND_SHORT, "[S");
            return array;
        }

        void* javan_char_array_new(int length) {
            unsigned long size = javan_array_allocation_size(sizeof(javan_char_array), length, sizeof(unsigned short));
            javan_char_array* array = (javan_char_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(unsigned short), JAVAN_ARRAY_KIND_CHAR, "[C");
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
            javan_runtime_lock_enter();
            javan_object_array* values = (javan_object_array*) javan_array_checked(array);
            javan_array_bounds_checked((javan_array_header*) values, index);
            values->values[index] = value;
            javan_runtime_lock_leave();
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

        static void javan_arrays_copy_of_into(
            void** result,
            void* array,
            int new_length,
            int expected_kind,
            void* (*allocate)(int)
        ) {
            if (result == NULL) {
                javan_panic("invalid array copy result");
            }
            javan_runtime_lock_enter();
            *result = NULL;
            javan_runtime_lock_leave();
            void* source_root = array;
            void** javan_array_copy_roots[] = {
                (void**) &source_root,
                result
            };
            javan_root_frame_push(javan_array_copy_roots, 2);
            javan_runtime_lock_enter();
            javan_array_header* source = javan_array_checked(source_root);
            javan_array_kind_checked(source, expected_kind);
            *result = allocate(new_length);
            source = javan_array_checked(source_root);
            javan_array_header* target = javan_array_checked(*result);
            int copied = source->length < new_length ? source->length : new_length;
            if (copied > 0) {
                memcpy(
                    javan_array_values(target),
                    javan_array_values(source),
                    (unsigned long) copied * (unsigned long) source->element_size
                );
            }
            javan_runtime_lock_leave();
            javan_root_frame_pop(javan_array_copy_roots);
        }

        void javan_arrays_copy_of_object_into(void** result, void* array, int new_length) {
            if (result == NULL) {
                javan_panic("invalid array copy result");
            }
            javan_runtime_lock_enter();
            *result = NULL;
            javan_runtime_lock_leave();
            void* source_root = array;
            void** javan_array_copy_roots[] = {
                (void**) &source_root,
                result
            };
            javan_root_frame_push(javan_array_copy_roots, 2);
            javan_runtime_lock_enter();
            javan_array_header* source = javan_array_checked(source_root);
            javan_array_kind_checked(source, JAVAN_ARRAY_KIND_OBJECT);
            *result = javan_object_array_new(new_length, source->class_name);
            source = javan_array_checked(source_root);
            javan_array_header* target = javan_array_checked(*result);
            int copied = source->length < new_length ? source->length : new_length;
            if (copied > 0) {
                memcpy(
                    javan_array_values(target),
                    javan_array_values(source),
                    (unsigned long) copied * (unsigned long) source->element_size
                );
            }
            javan_runtime_lock_leave();
            javan_root_frame_pop(javan_array_copy_roots);
        }

        void javan_arrays_copy_of_boolean_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_BOOLEAN, javan_boolean_array_new);
        }

        void javan_arrays_copy_of_int_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_INT, javan_int_array_new);
        }

        void javan_arrays_copy_of_long_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_LONG, javan_long_array_new);
        }

        void javan_arrays_copy_of_float_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_FLOAT, javan_float_array_new);
        }

        void javan_arrays_copy_of_double_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_DOUBLE, javan_double_array_new);
        }

        void javan_arrays_copy_of_byte_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_BYTE, javan_byte_array_new);
        }

        void javan_arrays_copy_of_short_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_SHORT, javan_short_array_new);
        }

        void javan_arrays_copy_of_char_into(void** result, void* array, int new_length) {
            javan_arrays_copy_of_into(result, array, new_length, JAVAN_ARRAY_KIND_CHAR, javan_char_array_new);
        }

        void* javan_arrays_copy_of_object(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_object_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_boolean(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_boolean_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_int(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_int_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_long(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_long_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_float(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_float_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_double(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_double_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_byte(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_byte_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_short(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_short_into(&result, array, new_length);
            return result;
        }

        void* javan_arrays_copy_of_char(void* array, int new_length) {
            void* result = NULL;
            javan_arrays_copy_of_char_into(&result, array, new_length);
            return result;
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
            void* result = javan_object_array_new(new_length, source->class_name);
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

        int javan_arrays_equals_byte(void* left, void* right) {
            if (left == NULL || right == NULL) {
                return left == right;
            }
            javan_byte_array* left_values = (javan_byte_array*) javan_array_checked(left);
            javan_byte_array* right_values = (javan_byte_array*) javan_array_checked(right);
            javan_array_kind_checked((javan_array_header*) left_values, JAVAN_ARRAY_KIND_BYTE);
            javan_array_kind_checked((javan_array_header*) right_values, JAVAN_ARRAY_KIND_BYTE);
            if (left == right) {
                return 1;
            }
            if (left_values->length != right_values->length) {
                return 0;
            }
            if (left_values->length == 0) {
                return 1;
            }
            return memcmp(
                left_values->values,
                right_values->values,
                (unsigned long) left_values->length
            ) == 0;
        }

        int javan_arrays_fill_byte(void* array, int value) {
            if (array == NULL) {
                return 1;
            }
            javan_byte_array* values = (javan_byte_array*) javan_array_checked(array);
            javan_array_kind_checked((javan_array_header*) values, JAVAN_ARRAY_KIND_BYTE);
            if (values->length > 0) {
                memset(values->values, (unsigned char) value, (unsigned long) values->length);
            }
            return 0;
        }

        int javan_arrays_fill_range_byte(void* array, int begin, int end, int value) {
            if (array == NULL) {
                return 1;
            }
            javan_byte_array* values = (javan_byte_array*) javan_array_checked(array);
            javan_array_kind_checked((javan_array_header*) values, JAVAN_ARRAY_KIND_BYTE);
            if (begin > end) {
                return 2;
            }
            if (begin < 0 || end > values->length) {
                return 3;
            }
            if (begin < end) {
                memset(values->values + begin, (unsigned char) value, (unsigned long) (end - begin));
            }
            return 0;
        }

        void* javan_string_array_from_args(int argc, char** argv) {
            int length = argc > 0 ? argc - 1 : 0;
            void* result = javan_object_array_new(length, "[Ljava.lang.String;");
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

        int javan_string_hash_code(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            const unsigned char* current = (const unsigned char*) value;
            uint32_t hash = 0U;
            while (*current != 0U) {
                unsigned int code_point = 0U;
                unsigned char first = *current++;
                if ((first & 0x80U) == 0U) {
                    code_point = first;
                } else if ((first & 0xE0U) == 0xC0U) {
                    unsigned char second = *current++;
                    if ((second & 0xC0U) != 0x80U) {
                        javan_panic("invalid UTF-8 string");
                    }
                    code_point = ((unsigned int) (first & 0x1FU) << 6) | (unsigned int) (second & 0x3FU);
                } else if ((first & 0xF0U) == 0xE0U) {
                    unsigned char second = *current++;
                    unsigned char third = *current++;
                    if ((second & 0xC0U) != 0x80U || (third & 0xC0U) != 0x80U) {
                        javan_panic("invalid UTF-8 string");
                    }
                    code_point = ((unsigned int) (first & 0x0FU) << 12)
                        | ((unsigned int) (second & 0x3FU) << 6)
                        | (unsigned int) (third & 0x3FU);
                } else if ((first & 0xF8U) == 0xF0U) {
                    unsigned char second = *current++;
                    unsigned char third = *current++;
                    unsigned char fourth = *current++;
                    if ((second & 0xC0U) != 0x80U || (third & 0xC0U) != 0x80U || (fourth & 0xC0U) != 0x80U) {
                        javan_panic("invalid UTF-8 string");
                    }
                    code_point = ((unsigned int) (first & 0x07U) << 18)
                        | ((unsigned int) (second & 0x3FU) << 12)
                        | ((unsigned int) (third & 0x3FU) << 6)
                        | (unsigned int) (fourth & 0x3FU);
                } else {
                    javan_panic("invalid UTF-8 string");
                }
                if (code_point <= 0xFFFFU) {
                    hash = (hash * 31U) + code_point;
                } else if (code_point <= 0x10FFFFU) {
                    unsigned int adjusted = code_point - 0x10000U;
                    unsigned int high = 0xD800U + (adjusted >> 10);
                    unsigned int low = 0xDC00U + (adjusted & 0x3FFU);
                    hash = (hash * 31U) + high;
                    hash = (hash * 31U) + low;
                } else {
                    javan_panic("invalid UTF-8 string");
                }
            }
            return (int) hash;
        }

        int javan_string_is_empty(const char* value) {
            return javan_string_length(value) == 0;
        }

        static unsigned int javan_utf8_next_code_point(const unsigned char** cursor) {
            const unsigned char* current = *cursor;
            unsigned char first = *current;
            if ((first & 0x80U) == 0U) {
                *cursor = current + 1;
                return first;
            }
            if (first >= 0xC2U && first <= 0xDFU) {
                unsigned char second = current[1];
                if ((second & 0xC0U) != 0x80U) {
                    javan_panic("invalid UTF-8 string");
                }
                *cursor = current + 2;
                return ((unsigned int) (first & 0x1FU) << 6)
                    | (unsigned int) (second & 0x3FU);
            }
            if (first >= 0xE0U && first <= 0xEFU) {
                unsigned char second = current[1];
                if ((second & 0xC0U) != 0x80U) {
                    javan_panic("invalid UTF-8 string");
                }
                unsigned char third = current[2];
                if ((third & 0xC0U) != 0x80U || (first == 0xE0U && second < 0xA0U)) {
                    javan_panic("invalid UTF-8 string");
                }
                *cursor = current + 3;
                return ((unsigned int) (first & 0x0FU) << 12)
                    | ((unsigned int) (second & 0x3FU) << 6)
                    | (unsigned int) (third & 0x3FU);
            }
            if (first >= 0xF0U && first <= 0xF4U) {
                unsigned char second = current[1];
                if ((second & 0xC0U) != 0x80U) {
                    javan_panic("invalid UTF-8 string");
                }
                unsigned char third = current[2];
                if ((third & 0xC0U) != 0x80U) {
                    javan_panic("invalid UTF-8 string");
                }
                unsigned char fourth = current[3];
                if ((fourth & 0xC0U) != 0x80U
                    || (first == 0xF0U && second < 0x90U)
                    || (first == 0xF4U && second > 0x8FU)) {
                    javan_panic("invalid UTF-8 string");
                }
                *cursor = current + 4;
                return ((unsigned int) (first & 0x07U) << 18)
                    | ((unsigned int) (second & 0x3FU) << 12)
                    | ((unsigned int) (third & 0x3FU) << 6)
                    | (unsigned int) (fourth & 0x3FU);
            }
            javan_panic("invalid UTF-8 string");
            return 0U;
        }

        int javan_string_is_blank(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            const unsigned char* current = (const unsigned char*) value;
            while (*current != 0U) {
                unsigned int code_point = javan_utf8_next_code_point(&current);
                if (!javan_character_is_whitespace((int) code_point)) {
                    return 0;
                }
            }
            return 1;
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

        int javan_string_last_index_of_string(const char* value, const char* needle) {
            int length = javan_string_length(value);
            return javan_string_last_index_of_string_from(value, needle, length);
        }

        int javan_string_last_index_of_string_from(const char* value, const char* needle, int from_index) {
            if (value == NULL || needle == NULL) {
                javan_panic("null string");
            }
            int length = javan_string_length(value);
            int needle_length = javan_string_length(needle);
            if (needle_length == 0) {
                if (from_index < 0) {
                    return -1;
                }
                return from_index > length ? length : from_index;
            }
            if (needle_length > length || from_index < 0) {
                return -1;
            }
            int start = from_index > length - needle_length ? length - needle_length : from_index;
            for (int index = start; index >= 0; index--) {
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

        int javan_string_starts_with_from(const char* left, const char* prefix, int from_index) {
            if (left == NULL || prefix == NULL) {
                javan_panic("null string");
            }
            size_t left_length = strlen(left);
            size_t prefix_length = strlen(prefix);
            if (from_index < 0) {
                return 0;
            }
            if ((size_t) from_index > left_length) {
                return 0;
            }
            if (prefix_length > left_length - (size_t) from_index) {
                return 0;
            }
            return strncmp(left + from_index, prefix, prefix_length) == 0;
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

        void* javan_string_repeat(const char* value, int count) {
            if (value == NULL) {
                javan_panic("null string");
            }
            if (count < 0) {
                javan_panic("negative string repeat count");
            }
            int length = javan_string_length(value);
            if (count == 0 || length == 0) {
                return javan_string_copy("");
            }
            if (count == 1) {
                return javan_string_copy(value);
            }
            if (length > INT_MAX / count) {
                javan_panic("string length overflow");
            }
            int repeated_length = length * count;
            void* source_root = (void*) value;
            void** javan_string_repeat_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_repeat_roots, 1);
            char* result = javan_string_alloc((unsigned long) repeated_length + 1UL);
            for (int index = 0; index < count; index++) {
                memcpy(result + (index * length), (const char*) source_root, (unsigned long) length);
            }
            result[repeated_length] = '\\0';
            javan_root_frame_pop(javan_string_repeat_roots);
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

        void* javan_string_strip_leading(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = (int) strlen(value);
            int begin = 0;
            while (begin < length && ((unsigned char) value[begin]) <= 32) {
                begin++;
            }
            return javan_string_substring_range(value, begin, length);
        }

        void* javan_string_strip_trailing(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int end = (int) strlen(value);
            while (end > 0 && ((unsigned char) value[end - 1]) <= 32) {
                end--;
            }
            return javan_string_substring_range(value, 0, end);
        }

        void* javan_string_to_lower_case(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = (int) strlen(value);
            void* source_root = (void*) value;
            void** javan_string_to_lower_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_to_lower_roots, 1);
            char* result = javan_string_alloc((unsigned long) length + 1UL);
            for (int index = 0; index < length; index++) {
                unsigned char ch = ((const unsigned char*) source_root)[index];
                if (ch >= 'A' && ch <= 'Z') {
                    result[index] = (char) (ch + 32);
                } else {
                    result[index] = (char) ch;
                }
            }
            result[length] = '\\0';
            javan_root_frame_pop(javan_string_to_lower_roots);
            return result;
        }

        void* javan_string_to_upper_case(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = (int) strlen(value);
            void* source_root = (void*) value;
            void** javan_string_to_upper_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_to_upper_roots, 1);
            char* result = javan_string_alloc((unsigned long) length + 1UL);
            for (int index = 0; index < length; index++) {
                unsigned char ch = ((const unsigned char*) source_root)[index];
                if (ch >= 'a' && ch <= 'z') {
                    result[index] = (char) (ch - 32);
                } else {
                    result[index] = (char) ch;
                }
            }
            result[length] = '\\0';
            javan_root_frame_pop(javan_string_to_upper_roots);
            return result;
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
    private static final String SOURCE_COLLECTIONS_HEAD = """
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

        static int javan_runtime_class_equals(void* left, void* right) {
            javan_runtime_class_state* left_state = javan_runtime_class_checked(left);
            javan_runtime_class_state* right_state = javan_runtime_class_checked(right);
            if (left_state->exact_type_id != right_state->exact_type_id
                || left_state->is_enum != right_state->is_enum
                || left_state->is_array != right_state->is_array
                || strcmp(left_state->binary_name, right_state->binary_name) != 0
                || left_state->assignable_count != right_state->assignable_count) {
                return 0;
            }
            for (int index = 0; index < left_state->assignable_count; index++) {
                if (left_state->assignable_type_ids[index] != right_state->assignable_type_ids[index]) {
                    return 0;
                }
            }
            return 1;
        }

        int javan_object_equals(void* left, void* right) {
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
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_BYTE) {
                return ((javan_boxed_byte*) left)->value == ((javan_boxed_byte*) right)->value;
            }
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_SHORT) {
                return ((javan_boxed_short*) left)->value == ((javan_boxed_short*) right)->value;
            }
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                return ((javan_boxed_character*) left)->value == ((javan_boxed_character*) right)->value;
            }
            if (left_type == 0 && right_type == 0
                && javan_probably_string_key(left) != 0
                && javan_probably_string_key(right) != 0) {
                return strcmp((const char*) left, (const char*) right) == 0;
            }
            javan_allocation_node* left_node = javan_find_allocation(left, NULL);
            javan_allocation_node* right_node = javan_find_allocation(right, NULL);
            if (left_node != NULL
                && right_node != NULL
                && left_node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS
                && right_node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS) {
                return javan_runtime_class_equals(left, right);
            }
            if (left_type != 0 || right_type != 0) {
                return 0;
            }
            return 0;
        }

        int javan_record_hash_combine(int current, int component) {
            uint32_t combined = ((uint32_t) current * 31U) + (uint32_t) component;
            return (int32_t) combined;
        }

        int javan_record_boolean_hash_code(int value) {
            return value != 0 ? 1231 : 1237;
        }

        int javan_record_long_hash_code(long long value) {
            uint64_t bits = (uint64_t) value;
            return (int32_t) (uint32_t) (bits ^ (bits >> 32));
        }

        int javan_record_float_hash_code(float value) {
            uint32_t bits = 0U;
            memcpy(&bits, &value, sizeof(bits));
            if (isnan(value)) {
                bits = 0x7fc00000U;
            }
            return (int32_t) bits;
        }

        int javan_record_double_hash_code(double value) {
            uint64_t bits = 0U;
            memcpy(&bits, &value, sizeof(bits));
            if (isnan(value)) {
                bits = 0x7ff8000000000000ULL;
            }
            return (int32_t) (uint32_t) (bits ^ (bits >> 32));
        }

        int javan_record_float_equals(float left, float right) {
            if (left == right) {
                return left == 0.0f ? signbit(left) == signbit(right) : 1;
            }
            return isnan(left) && isnan(right);
        }

        int javan_record_double_equals(double left, double right) {
            if (left == right) {
                return left == 0.0 ? signbit(left) == signbit(right) : 1;
            }
            return isnan(left) && isnan(right);
        }

        int javan_record_reference_identity_equals(void* left, void* right) {
            return left == right;
        }

        int javan_record_reference_identity_hash_code(void* value) {
            if (value == NULL) {
                return 0;
            }
            uintptr_t bits = (uintptr_t) value;
            bits >>= 3;
            bits ^= bits >> 17;
            bits *= (uintptr_t) 0xed5ad4bbU;
            bits ^= bits >> 11;
            return (int32_t) (uint32_t) bits;
        }

        int javan_record_shape_exact_type(void* value, int expected_type_id) {
            if (value == NULL) {
                return 1;
            }
            int actual_type_id = javan_registered_type_id(value);
            if (actual_type_id != 0) {
                return actual_type_id == expected_type_id;
            }
            return javan_record_exact_type_resolver_value == NULL
                ? 0
                : javan_record_exact_type_resolver_value(value, expected_type_id);
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
            list->view_flags = 0;
            list->reserved = 0;
            list->backing = NULL;
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

        static javan_object_list* javan_list_new_view(javan_object_list* backing, int immutable, int view_flags) {
            if (backing == NULL) {
                javan_panic("null list backing");
            }
            void** roots[] = {
                (void**) &backing
            };
            javan_root_frame_push(roots, 1);
            javan_object_list* list = (javan_object_list*) javan_alloc(sizeof(javan_object_list));
            list->magic = JAVAN_OBJECT_LIST_MAGIC;
            list->length = 0;
            list->capacity = 0;
            list->immutable = immutable;
            list->mod_count = 0;
            list->view_flags = view_flags;
            list->reserved = 0;
            list->backing = backing;
            list->values = NULL;
            javan_update_runtime_allocation_kind((void*) list, JAVAN_RUNTIME_KIND_OBJECT_LIST);
            javan_root_frame_pop(roots);
            return list;
        }

        static int javan_list_is_set(javan_object_list* list) {
            if ((list->view_flags & JAVAN_LIST_VIEW_SET) != 0) {
                return 1;
            }
            if (list->backing != NULL) {
                return javan_list_is_set(list->backing);
            }
            return 0;
        }

        static javan_object_list* javan_list_storage_owner(javan_object_list* list) {
            while (list->backing != NULL) {
                list = list->backing;
            }
            return list;
        }

        void* javan_list_unmodifiable(void* value) {
            javan_object_list* list = javan_list_checked(value);
            if (list->immutable != 0 && list->backing != NULL && (list->view_flags & JAVAN_LIST_VIEW_UNMODIFIABLE) != 0) {
                return list;
            }
            return javan_list_new_view(list, 1, JAVAN_LIST_VIEW_UNMODIFIABLE);
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

        static int javan_list_logical_length(javan_object_list* list) {
            if (list->backing != NULL) {
                return javan_list_logical_length(list->backing);
            }
            return list->length;
        }

        static int javan_list_observed_mod_count(javan_object_list* list) {
            if (list->backing != NULL) {
                return javan_list_observed_mod_count(list->backing);
            }
            return list->mod_count;
        }

        static void javan_list_iterator_index_checked(javan_object_list* list, int index) {
            int length = javan_list_logical_length(list);
            if (index < 0 || index > length) {
                javan_panic("list iterator index out of bounds");
            }
        }

        static void javan_list_iterator_state_checked(javan_object_iterator* iterator) {
            if (iterator->expected_mod_count != javan_list_observed_mod_count(iterator->list)) {
                javan_panic("concurrent list modification");
            }
        }

        static void* javan_list_get_unchecked(javan_object_list* list, int index) {
            if (list->backing != NULL) {
                return javan_list_get_unchecked(list->backing, index);
            }
            return list->values[index];
        }

        """;
    private static final String SOURCE_RECORD_SHAPES = """
        static void javan_record_shape_mismatch(void) {
            javan_panic("record generic value does not match declared shape");
        }

        static int javan_record_shape_type_id(const char* shape) {
            int index = 1;
            int sign = 1;
            int value = 0;
            if (shape[index] == '-') {
                sign = -1;
                index++;
            }
            int digit_count = 0;
            while (shape[index] >= '0' && shape[index] <= '9') {
                value = (value * 10) + (shape[index] - '0');
                index++;
                digit_count++;
            }
            if (digit_count == 0 || shape[index] != ';' || shape[index + 1] != '\\0') {
                javan_panic("invalid generated record shape");
            }
            return sign * value;
        }

        static const char* javan_record_shape_array_name(const char* shape) {
            int index = 1;
            int length = 0;
            int digit_count = 0;
            while (shape[index] >= '0' && shape[index] <= '9') {
                length = (length * 10) + (shape[index] - '0');
                index++;
                digit_count++;
            }
            if (digit_count == 0 || shape[index] != ':') {
                javan_panic("invalid generated record shape");
            }
            const char* name = shape + index + 1;
            if ((int) strlen(name) != length) {
                javan_panic("invalid generated record shape");
            }
            return name;
        }

        static int javan_record_shape_array_assignable(void* value, const char* expected_name) {
            void* value_root = value;
            void* expected_class = NULL;
            void* actual_class = NULL;
            void** roots[] = {
                (void**) &value_root,
                (void**) &expected_class,
                (void**) &actual_class
            };
            javan_root_frame_push(roots, 3);
            expected_class = javan_runtime_class_from_binary_name(expected_name);
            actual_class = javan_object_get_class(value_root);
            int result = javan_class_is_assignable_from(expected_class, actual_class);
            javan_root_frame_pop(roots);
            return result;
        }

        static int javan_record_boxed_equals(void* left, void* right, int type_id) {
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return ((javan_boxed_int*) left)->value == ((javan_boxed_int*) right)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return ((javan_boxed_long*) left)->value == ((javan_boxed_long*) right)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_record_float_equals(
                    ((javan_boxed_float*) left)->value,
                    ((javan_boxed_float*) right)->value
                );
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_record_double_equals(
                    ((javan_boxed_double*) left)->value,
                    ((javan_boxed_double*) right)->value
                );
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return ((javan_boxed_boolean*) left)->value == ((javan_boxed_boolean*) right)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BYTE) {
                return ((javan_boxed_byte*) left)->value == ((javan_boxed_byte*) right)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_SHORT) {
                return ((javan_boxed_short*) left)->value == ((javan_boxed_short*) right)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                return ((javan_boxed_character*) left)->value == ((javan_boxed_character*) right)->value;
            }
            javan_panic("invalid generated record shape");
            return 0;
        }

        static int javan_record_boxed_hash_code(void* value, int type_id) {
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) return ((javan_boxed_int*) value)->value;
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return javan_record_long_hash_code(((javan_boxed_long*) value)->value);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_record_float_hash_code(((javan_boxed_float*) value)->value);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_record_double_hash_code(((javan_boxed_double*) value)->value);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return javan_record_boolean_hash_code(((javan_boxed_boolean*) value)->value);
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BYTE) return ((javan_boxed_byte*) value)->value;
            if (type_id == JAVAN_TYPE_JAVA_LANG_SHORT) return ((javan_boxed_short*) value)->value;
            if (type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER) return ((javan_boxed_character*) value)->value;
            javan_panic("invalid generated record shape");
            return 0;
        }

        void javan_record_shape_validate(void* value, const char* shape) {
            if (shape == NULL || shape[0] == '\\0') {
                javan_panic("invalid generated record shape");
            }
            if (shape[0] == 's') {
                if (shape[1] != '\\0') {
                    javan_panic("invalid generated record shape");
                }
                if (value == NULL) {
                    return;
                }
                javan_allocation_node* node = javan_find_allocation(value, NULL);
                if (javan_registered_type_id(value) != 0
                    || (node != NULL && node->runtime_kind != JAVAN_RUNTIME_KIND_STRING)
                    || (node == NULL && javan_probably_string_key(value) == 0)) {
                    javan_record_shape_mismatch();
                }
                return;
            }
            if (shape[0] == 'b') {
                int expected_type_id = javan_record_shape_type_id(shape);
                if (value != NULL && javan_registered_type_id(value) != expected_type_id) {
                    javan_record_shape_mismatch();
                }
                return;
            }
            if (shape[0] == 'o' || shape[0] == 'e') {
                int expected_type_id = javan_record_shape_type_id(shape);
                if (value != NULL && javan_record_shape_exact_type(value, expected_type_id) == 0) {
                    javan_record_shape_mismatch();
                }
                return;
            }
            if (shape[0] == 'a') {
                const char* expected_name = javan_record_shape_array_name(shape);
                if (value == NULL) {
                    return;
                }
                javan_allocation_node* node = javan_find_allocation(value, NULL);
                if (node == NULL
                    || node->kind != JAVAN_HEAP_KIND_ARRAY
                    || node->array_class_name == NULL
                    || javan_record_shape_array_assignable(value, expected_name) == 0) {
                    javan_record_shape_mismatch();
                }
                return;
            }
            if (shape[0] == 'l') {
                if (shape[1] == '\\0') {
                    javan_panic("invalid generated record shape");
                }
                if (value == NULL) {
                    return;
                }
                javan_allocation_node* node = javan_find_allocation(value, NULL);
                if (node == NULL || node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_LIST) {
                    javan_record_shape_mismatch();
                }
                javan_object_list* list = (javan_object_list*) value;
                if (list->magic != JAVAN_OBJECT_LIST_MAGIC) {
                    javan_record_shape_mismatch();
                }
                int length = javan_list_logical_length(list);
                for (int index = 0; index < length; index++) {
                    javan_record_shape_validate(javan_list_get_unchecked(list, index), shape + 1);
                }
                return;
            }
            javan_panic("invalid generated record shape");
        }

        int javan_record_shape_equals_prevalidated(void* left, void* right, const char* shape) {
            if (left == right) {
                return 1;
            }
            if (left == NULL || right == NULL) {
                return 0;
            }
            if (shape[0] == 'a') {
                return 0;
            }
            if (shape[0] == 'e') {
                return left == right;
            }
            if (shape[0] == 's') {
                return strcmp((const char*) left, (const char*) right) == 0;
            }
            if (shape[0] == 'b') {
                return javan_record_boxed_equals(left, right, javan_record_shape_type_id(shape));
            }
            if (shape[0] == 'o') {
                return javan_record_object_equals_resolver_value == NULL
                    ? javan_record_reference_identity_equals(left, right)
                    : javan_record_object_equals_resolver_value(left, right);
            }
            if (shape[0] != 'l') {
                javan_panic("invalid generated record shape");
                return 0;
            }
            javan_object_list* left_list = (javan_object_list*) left;
            javan_object_list* right_list = (javan_object_list*) right;
            int length = javan_list_logical_length(left_list);
            if (length != javan_list_logical_length(right_list)) {
                return 0;
            }
            for (int index = 0; index < length; index++) {
                if (javan_record_shape_equals_prevalidated(
                    javan_list_get_unchecked(left_list, index),
                    javan_list_get_unchecked(right_list, index),
                    shape + 1
                ) == 0) {
                    return 0;
                }
            }
            return 1;
        }

        int javan_record_shape_equals(void* left, void* right, const char* shape) {
            javan_record_shape_validate(left, shape);
            javan_record_shape_validate(right, shape);
            return javan_record_shape_equals_prevalidated(left, right, shape);
        }

        static int javan_record_shape_hash_code_valid(void* value, const char* shape) {
            if (value == NULL) {
                return 0;
            }
            if (shape[0] == 'a') {
                return javan_record_reference_identity_hash_code(value);
            }
            if (shape[0] == 'e') {
                return javan_record_reference_identity_hash_code(value);
            }
            if (shape[0] == 's') {
                return javan_string_hash_code((const char*) value);
            }
            if (shape[0] == 'b') {
                return javan_record_boxed_hash_code(value, javan_record_shape_type_id(shape));
            }
            if (shape[0] == 'o') {
                return javan_record_object_hash_code_resolver_value == NULL
                    ? javan_record_reference_identity_hash_code(value)
                    : javan_record_object_hash_code_resolver_value(value);
            }
            if (shape[0] != 'l') {
                javan_panic("invalid generated record shape");
                return 0;
            }
            javan_object_list* list = (javan_object_list*) value;
            uint32_t hash = 1U;
            int length = javan_list_logical_length(list);
            for (int index = 0; index < length; index++) {
                hash = (hash * 31U) + (uint32_t) javan_record_shape_hash_code_valid(
                    javan_list_get_unchecked(list, index),
                    shape + 1
                );
            }
            return (int32_t) hash;
        }

        int javan_record_shape_hash_code(void* value, const char* shape) {
            javan_record_shape_validate(value, shape);
            return javan_record_shape_hash_code_valid(value, shape);
        }

        """;
    private static final String SOURCE_COLLECTIONS_HEAD_CONTINUED = """
        static void javan_list_mutable_checked(javan_object_list* list) {
            if (list->immutable != 0) {
                javan_panic("unsupported operation on immutable list");
            }
        }

        static void javan_list_bounds_checked(javan_object_list* list, int index) {
            int length = javan_list_logical_length(list);
            if (index < 0 || index >= length) {
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

        int javan_collection_add(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            if (javan_list_is_set(list) != 0) {
                return javan_set_add(value, element);
            }
            return javan_arraylist_add(value, element);
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

        int javan_arraylist_add_all_at(void* value, int index, void* collection) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* source = javan_list_checked(collection);
            javan_list_mutable_checked(list);
            if (index < 0 || index > list->length) {
                javan_panic("list index out of bounds");
            }
            int copied = source->length;
            if (copied == 0) {
                return 0;
            }
            void** javan_list_add_all_at_roots[] = {
                (void**) &list,
                (void**) &source
            };
            javan_root_frame_push(javan_list_add_all_at_roots, 2);
            javan_list_ensure_capacity(list, list->length + copied);
            if (index < list->length) {
                memmove(
                    list->values + index + copied,
                    list->values + index,
                    (unsigned long) (list->length - index) * sizeof(void*)
                );
            }
            for (int source_index = 0; source_index < copied; source_index++) {
                list->values[index + source_index] = source->values[source_index];
            }
            list->length += copied;
            javan_root_frame_pop(javan_list_add_all_at_roots);
            list->mod_count++;
            return 1;
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

        int javan_collection_add_all(void* value, void* collection) {
            javan_object_list* list = javan_list_checked(value);
            if (javan_list_is_set(list) != 0) {
                return javan_hashset_add_all(value, collection);
            }
            return javan_arraylist_add_all(value, collection);
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

        void javan_arraylist_add_last(void* value, void* element) {
            javan_arraylist_add(value, element);
        }

        void* javan_arraylist_set(void* value, int index, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            javan_list_bounds_checked(list, index);
            void* previous = list->values[index];
            list->values[index] = element;
            return previous;
        }

        void* javan_arraylist_remove_at(void* value, int index) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            javan_list_bounds_checked(list, index);
            void* previous = list->values[index];
            if (index + 1 < list->length) {
                memmove(list->values + index, list->values + index + 1, (unsigned long) (list->length - index - 1) * sizeof(void*));
            }
            list->length--;
            list->values[list->length] = NULL;
            list->mod_count++;
            return previous;
        }

        void* javan_arraylist_remove_first(void* value) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            if (list->length == 0) {
                javan_panic("list is empty");
            }
            void* previous = list->values[0];
            if (list->length > 1) {
                memmove(list->values, list->values + 1, (unsigned long) (list->length - 1) * sizeof(void*));
            }
            list->length--;
            list->values[list->length] = NULL;
            list->mod_count++;
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
            int length = javan_list_logical_length(source);
            javan_object_list* list = javan_list_new_with_capacity(length, 1);
            for (int index = 0; index < length; index++) {
                javan_list_append_raw(list, javan_list_get_unchecked(source, index));
            }
            javan_root_frame_pop(javan_list_copy_roots);
            return list;
        }

        void* javan_list_to_array(void* value) {
            javan_object_list* source = javan_list_checked(value);
            void** javan_list_to_array_roots[] = {
                (void**) &source
            };
            javan_root_frame_push(javan_list_to_array_roots, 1);
            int length = javan_list_logical_length(source);
            void* array = javan_object_array_new(length, "[Ljava.lang.Object;");
            for (int index = 0; index < length; index++) {
                javan_object_array_set(array, index, javan_list_get_unchecked(source, index));
            }
            javan_root_frame_pop(javan_list_to_array_roots);
            return array;
        }

        int javan_list_size(void* value) {
            return javan_list_logical_length(javan_list_checked(value));
        }

        int javan_list_is_empty(void* value) {
            return javan_list_logical_length(javan_list_checked(value)) == 0;
        }

        int javan_list_contains(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            int length = javan_list_logical_length(list);
            for (int index = 0; index < length; index++) {
                if (javan_object_equals(javan_list_get_unchecked(list, index), element) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        int javan_list_index_of(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            int length = javan_list_logical_length(list);
            for (int index = 0; index < length; index++) {
                if (javan_object_equals(javan_list_get_unchecked(list, index), element) != 0) {
                    return index;
                }
            }
            return -1;
        }

        int javan_list_last_index_of(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            for (int index = javan_list_logical_length(list) - 1; index >= 0; index--) {
                if (javan_object_equals(javan_list_get_unchecked(list, index), element) != 0) {
                    return index;
                }
            }
            return -1;
        }

        int javan_list_remove(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            int length = javan_list_logical_length(list);
            for (int index = 0; index < length; index++) {
                if (javan_object_equals(javan_list_get_unchecked(list, index), element) == 0) {
                    continue;
                }
                if (index + 1 < length) {
                    memmove(list->values + index, list->values + index + 1, (unsigned long) (length - index - 1) * sizeof(void*));
                }
                list->length--;
                list->values[list->length] = NULL;
                list->mod_count++;
                return 1;
            }
            return 0;
        }

        int javan_list_remove_all(void* value, void* other) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* probe = javan_list_checked(other);
            javan_list_mutable_checked(list);
            if (javan_list_storage_owner(list) == javan_list_storage_owner(probe)) {
                int changed = javan_list_logical_length(list) != 0;
                if (changed != 0) {
                    javan_list_clear(value);
                }
                return changed;
            }
            void** javan_list_remove_all_roots[] = {
                (void**) &list,
                (void**) &probe
            };
            javan_root_frame_push(javan_list_remove_all_roots, 2);
            int length = javan_list_logical_length(list);
            int write_index = 0;
            int changed = 0;
            for (int index = 0; index < length; index++) {
                void* element = javan_list_get_unchecked(list, index);
                if (javan_list_contains(probe, element) != 0) {
                    changed = 1;
                    continue;
                }
                list->values[write_index] = element;
                write_index++;
            }
            if (changed != 0) {
                memset(list->values + write_index, 0, (unsigned long) (length - write_index) * sizeof(void*));
                list->length = write_index;
                list->mod_count++;
            }
            javan_root_frame_pop(javan_list_remove_all_roots);
            return changed;
        }

        int javan_list_retain_all(void* value, void* other) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* probe = javan_list_checked(other);
            javan_list_mutable_checked(list);
            if (javan_list_storage_owner(list) == javan_list_storage_owner(probe)) {
                return 0;
            }
            void** javan_list_retain_all_roots[] = {
                (void**) &list,
                (void**) &probe
            };
            javan_root_frame_push(javan_list_retain_all_roots, 2);
            int length = javan_list_logical_length(list);
            int write_index = 0;
            int changed = 0;
            for (int index = 0; index < length; index++) {
                void* element = javan_list_get_unchecked(list, index);
                if (javan_list_contains(probe, element) == 0) {
                    changed = 1;
                    continue;
                }
                list->values[write_index] = element;
                write_index++;
            }
            if (changed != 0) {
                memset(list->values + write_index, 0, (unsigned long) (length - write_index) * sizeof(void*));
                list->length = write_index;
                list->mod_count++;
            }
            javan_root_frame_pop(javan_list_retain_all_roots);
            return changed;
        }

        int javan_list_contains_all(void* value, void* other) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* probe = javan_list_checked(other);
            void** javan_list_contains_all_roots[] = {
                (void**) &list,
                (void**) &probe
            };
            javan_root_frame_push(javan_list_contains_all_roots, 2);
            int probe_length = javan_list_logical_length(probe);
            for (int index = 0; index < probe_length; index++) {
                if (javan_list_contains(list, javan_list_get_unchecked(probe, index)) == 0) {
                    javan_root_frame_pop(javan_list_contains_all_roots);
                    return 0;
                }
            }
            javan_root_frame_pop(javan_list_contains_all_roots);
            return 1;
        }

        void javan_list_clear(void* value) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            int length = javan_list_logical_length(list);
            if (length == 0) {
                return;
            }
            memset(list->values, 0, (unsigned long) length * sizeof(void*));
            list->length = 0;
            list->mod_count++;
        }

        void* javan_list_get(void* value, int index) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_bounds_checked(list, index);
            return javan_list_get_unchecked(list, index);
        }

        void* javan_list_get_first(void* value) {
            return javan_list_get(value, 0);
        }

        void* javan_list_get_last(void* value) {
            javan_object_list* list = javan_list_checked(value);
            int length = javan_list_logical_length(list);
            if (length == 0) {
                javan_panic("list is empty");
            }
            return javan_list_get_unchecked(list, length - 1);
        }

        void* javan_list_iterator_at(void* value, int index) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_iterator_index_checked(list, index);
            void** javan_list_iterator_roots[] = {
                (void**) &list
            };
            javan_root_frame_push(javan_list_iterator_roots, 1);
            javan_object_iterator* iterator = (javan_object_iterator*) javan_alloc(sizeof(javan_object_iterator));
            iterator->magic = JAVAN_OBJECT_ITERATOR_MAGIC;
            iterator->index = index;
            iterator->expected_mod_count = javan_list_observed_mod_count(list);
            iterator->reserved = -1;
            iterator->list = list;
            javan_update_runtime_allocation_kind((void*) iterator, JAVAN_RUNTIME_KIND_OBJECT_ITERATOR);
            javan_root_frame_pop(javan_list_iterator_roots);
            return iterator;
        }

        void* javan_list_iterator(void* value) {
            return javan_list_iterator_at(value, 0);
        }

        int javan_iterator_has_next(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            return iterator->index < javan_list_logical_length(iterator->list);
        }

        void* javan_iterator_next(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            javan_list_iterator_state_checked(iterator);
            if (iterator->index >= javan_list_logical_length(iterator->list)) {
                javan_panic("iterator exhausted");
            }
            void* result = javan_list_get_unchecked(iterator->list, iterator->index);
            iterator->reserved = iterator->index;
            iterator->index++;
            return result;
        }

        int javan_list_iterator_has_previous(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            return iterator->index > 0;
        }

        void* javan_list_iterator_previous(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            javan_list_iterator_state_checked(iterator);
            if (iterator->index <= 0) {
                javan_panic("iterator exhausted");
            }
            iterator->index--;
            iterator->reserved = iterator->index;
            return javan_list_get_unchecked(iterator->list, iterator->index);
        }

        int javan_list_iterator_next_index(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            return iterator->index;
        }

        int javan_list_iterator_previous_index(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            return iterator->index - 1;
        }

        void javan_list_iterator_remove(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            javan_list_iterator_state_checked(iterator);
            if (iterator->reserved < 0) {
                javan_panic("invalid iterator state");
            }
            int removed = iterator->reserved;
            javan_arraylist_remove_at(iterator->list, removed);
            if (removed < iterator->index) {
                iterator->index--;
            }
            iterator->reserved = -1;
            iterator->expected_mod_count = javan_list_observed_mod_count(iterator->list);
        }

        void javan_list_iterator_set(void* value, void* element) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            javan_list_iterator_state_checked(iterator);
            if (iterator->reserved < 0) {
                javan_panic("invalid iterator state");
            }
            javan_arraylist_set(iterator->list, iterator->reserved, element);
        }

        void javan_list_iterator_add(void* value, void* element) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            javan_list_iterator_state_checked(iterator);
            javan_arraylist_add_at(iterator->list, iterator->index, element);
            iterator->index++;
            iterator->reserved = -1;
            iterator->expected_mod_count = javan_list_observed_mod_count(iterator->list);
        }

        void* javan_hashset_new(void) {
            javan_object_list* set = javan_list_new_with_capacity(0, 0);
            set->view_flags = JAVAN_LIST_VIEW_SET;
            return set;
        }

        int javan_hashset_add_all(void* value, void* collection) {
            javan_object_list* set = javan_list_checked(value);
            javan_object_list* source = javan_list_checked(collection);
            javan_list_mutable_checked(set);
            void** javan_hashset_add_all_roots[] = {
                (void**) &set,
                (void**) &source
            };
            javan_root_frame_push(javan_hashset_add_all_roots, 2);
            int length = javan_list_logical_length(source);
            int changed = 0;
            for (int index = 0; index < length; index++) {
                if (javan_set_add(set, javan_list_get_unchecked(source, index)) != 0) {
                    changed = 1;
                }
            }
            javan_root_frame_pop(javan_hashset_add_all_roots);
            return changed;
        }

        void* javan_set_empty(void) {
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            set->immutable = 1;
            return set;
        }

        void* javan_set_copy_of(void* collection) {
            javan_object_list* source = javan_list_checked(collection);
            void* result_value = NULL;
            void** javan_set_copy_roots[] = {
                (void**) &source,
                (void**) &result_value
            };
            javan_root_frame_push(javan_set_copy_roots, 2);
            int length = javan_list_logical_length(source);
            result_value = javan_hashset_new();
            for (int index = 0; index < length; index++) {
                javan_set_add(result_value, javan_list_get_unchecked(source, index));
            }
            ((javan_object_list*) result_value)->immutable = 1;
            javan_root_frame_pop(javan_set_copy_roots);
            return result_value;
        }

        void* javan_set_of_singleton(void* value) {
            if (value == NULL) {
                javan_panic("null Set.of element");
            }
            return javan_set_singleton(value);
        }

        void* javan_set_of_pair(void* left, void* right) {
            if (left == NULL || right == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(left, right) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* left_root = left;
            void* right_root = right;
            void** javan_set_of_pair_roots[] = {
                (void**) &left_root,
                (void**) &right_root
            };
            javan_root_frame_push(javan_set_of_pair_roots, 2);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, left_root);
            javan_set_add(set, right_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_pair_roots);
            return set;
        }

        void* javan_set_of_triple(void* left, void* middle, void* right) {
            if (left == NULL || middle == NULL || right == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(left, middle) != 0
                || javan_object_equals(left, right) != 0
                || javan_object_equals(middle, right) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* left_root = left;
            void* middle_root = middle;
            void* right_root = right;
            void** javan_set_of_triple_roots[] = {
                (void**) &left_root,
                (void**) &middle_root,
                (void**) &right_root
            };
            javan_root_frame_push(javan_set_of_triple_roots, 3);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, left_root);
            javan_set_add(set, middle_root);
            javan_set_add(set, right_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_triple_roots);
            return set;
        }

        void* javan_set_of_quadruple(void* first, void* second, void* third, void* fourth) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(third, fourth) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void** javan_set_of_quadruple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root
            };
            javan_root_frame_push(javan_set_of_quadruple_roots, 4);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_quadruple_roots);
            return set;
        }

        void* javan_set_of_quintuple(void* first, void* second, void* third, void* fourth, void* fifth) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL || fifth == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(first, fifth) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(second, fifth) != 0
                || javan_object_equals(third, fourth) != 0
                || javan_object_equals(third, fifth) != 0
                || javan_object_equals(fourth, fifth) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void* fifth_root = fifth;
            void** javan_set_of_quintuple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root,
                (void**) &fifth_root
            };
            javan_root_frame_push(javan_set_of_quintuple_roots, 5);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            javan_set_add(set, fifth_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_quintuple_roots);
            return set;
        }

        void* javan_set_of_sextuple(void* first, void* second, void* third, void* fourth, void* fifth, void* sixth) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL || fifth == NULL || sixth == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(first, fifth) != 0
                || javan_object_equals(first, sixth) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(second, fifth) != 0
                || javan_object_equals(second, sixth) != 0
                || javan_object_equals(third, fourth) != 0
                || javan_object_equals(third, fifth) != 0
                || javan_object_equals(third, sixth) != 0
                || javan_object_equals(fourth, fifth) != 0
                || javan_object_equals(fourth, sixth) != 0
                || javan_object_equals(fifth, sixth) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void* fifth_root = fifth;
            void* sixth_root = sixth;
            void** javan_set_of_sextuple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root,
                (void**) &fifth_root,
                (void**) &sixth_root
            };
            javan_root_frame_push(javan_set_of_sextuple_roots, 6);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            javan_set_add(set, fifth_root);
            javan_set_add(set, sixth_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_sextuple_roots);
            return set;
        }

        void* javan_set_of_septuple(void* first, void* second, void* third, void* fourth, void* fifth, void* sixth, void* seventh) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL || fifth == NULL || sixth == NULL || seventh == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(first, fifth) != 0
                || javan_object_equals(first, sixth) != 0
                || javan_object_equals(first, seventh) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(second, fifth) != 0
                || javan_object_equals(second, sixth) != 0
                || javan_object_equals(second, seventh) != 0
                || javan_object_equals(third, fourth) != 0
                || javan_object_equals(third, fifth) != 0
                || javan_object_equals(third, sixth) != 0
                || javan_object_equals(third, seventh) != 0
                || javan_object_equals(fourth, fifth) != 0
                || javan_object_equals(fourth, sixth) != 0
                || javan_object_equals(fourth, seventh) != 0
                || javan_object_equals(fifth, sixth) != 0
                || javan_object_equals(fifth, seventh) != 0
                || javan_object_equals(sixth, seventh) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void* fifth_root = fifth;
            void* sixth_root = sixth;
            void* seventh_root = seventh;
            void** javan_set_of_septuple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root,
                (void**) &fifth_root,
                (void**) &sixth_root,
                (void**) &seventh_root
            };
            javan_root_frame_push(javan_set_of_septuple_roots, 7);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            javan_set_add(set, fifth_root);
            javan_set_add(set, sixth_root);
            javan_set_add(set, seventh_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_septuple_roots);
            return set;
        }

        void* javan_set_of_octuple(void* first, void* second, void* third, void* fourth, void* fifth, void* sixth, void* seventh, void* eighth) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL || fifth == NULL || sixth == NULL || seventh == NULL || eighth == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(first, fifth) != 0
                || javan_object_equals(first, sixth) != 0
                || javan_object_equals(first, seventh) != 0
                || javan_object_equals(first, eighth) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(second, fifth) != 0
                || javan_object_equals(second, sixth) != 0
                || javan_object_equals(second, seventh) != 0
                || javan_object_equals(second, eighth) != 0
                || javan_object_equals(third, fourth) != 0
                || javan_object_equals(third, fifth) != 0
                || javan_object_equals(third, sixth) != 0
                || javan_object_equals(third, seventh) != 0
                || javan_object_equals(third, eighth) != 0
                || javan_object_equals(fourth, fifth) != 0
                || javan_object_equals(fourth, sixth) != 0
                || javan_object_equals(fourth, seventh) != 0
                || javan_object_equals(fourth, eighth) != 0
                || javan_object_equals(fifth, sixth) != 0
                || javan_object_equals(fifth, seventh) != 0
                || javan_object_equals(fifth, eighth) != 0
                || javan_object_equals(sixth, seventh) != 0
                || javan_object_equals(sixth, eighth) != 0
                || javan_object_equals(seventh, eighth) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void* fifth_root = fifth;
            void* sixth_root = sixth;
            void* seventh_root = seventh;
            void* eighth_root = eighth;
            void** javan_set_of_octuple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root,
                (void**) &fifth_root,
                (void**) &sixth_root,
                (void**) &seventh_root,
                (void**) &eighth_root
            };
            javan_root_frame_push(javan_set_of_octuple_roots, 8);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            javan_set_add(set, fifth_root);
            javan_set_add(set, sixth_root);
            javan_set_add(set, seventh_root);
            javan_set_add(set, eighth_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_octuple_roots);
            return set;
        }

        void* javan_set_of_nonuple(void* first, void* second, void* third, void* fourth, void* fifth, void* sixth, void* seventh, void* eighth, void* ninth) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL || fifth == NULL || sixth == NULL || seventh == NULL || eighth == NULL || ninth == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(first, fifth) != 0
                || javan_object_equals(first, sixth) != 0
                || javan_object_equals(first, seventh) != 0
                || javan_object_equals(first, eighth) != 0
                || javan_object_equals(first, ninth) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(second, fifth) != 0
                || javan_object_equals(second, sixth) != 0
                || javan_object_equals(second, seventh) != 0
                || javan_object_equals(second, eighth) != 0
                || javan_object_equals(second, ninth) != 0
                || javan_object_equals(third, fourth) != 0
                || javan_object_equals(third, fifth) != 0
                || javan_object_equals(third, sixth) != 0
                || javan_object_equals(third, seventh) != 0
                || javan_object_equals(third, eighth) != 0
                || javan_object_equals(third, ninth) != 0
                || javan_object_equals(fourth, fifth) != 0
                || javan_object_equals(fourth, sixth) != 0
                || javan_object_equals(fourth, seventh) != 0
                || javan_object_equals(fourth, eighth) != 0
                || javan_object_equals(fourth, ninth) != 0
                || javan_object_equals(fifth, sixth) != 0
                || javan_object_equals(fifth, seventh) != 0
                || javan_object_equals(fifth, eighth) != 0
                || javan_object_equals(fifth, ninth) != 0
                || javan_object_equals(sixth, seventh) != 0
                || javan_object_equals(sixth, eighth) != 0
                || javan_object_equals(sixth, ninth) != 0
                || javan_object_equals(seventh, eighth) != 0
                || javan_object_equals(seventh, ninth) != 0
                || javan_object_equals(eighth, ninth) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void* fifth_root = fifth;
            void* sixth_root = sixth;
            void* seventh_root = seventh;
            void* eighth_root = eighth;
            void* ninth_root = ninth;
            void** javan_set_of_nonuple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root,
                (void**) &fifth_root,
                (void**) &sixth_root,
                (void**) &seventh_root,
                (void**) &eighth_root,
                (void**) &ninth_root
            };
            javan_root_frame_push(javan_set_of_nonuple_roots, 9);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            javan_set_add(set, fifth_root);
            javan_set_add(set, sixth_root);
            javan_set_add(set, seventh_root);
            javan_set_add(set, eighth_root);
            javan_set_add(set, ninth_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_nonuple_roots);
            return set;
        }

        void* javan_set_of_decuple(void* first, void* second, void* third, void* fourth, void* fifth, void* sixth, void* seventh, void* eighth, void* ninth, void* tenth) {
            if (first == NULL || second == NULL || third == NULL || fourth == NULL || fifth == NULL || sixth == NULL || seventh == NULL || eighth == NULL || ninth == NULL || tenth == NULL) {
                javan_panic("null Set.of element");
            }
            if (javan_object_equals(first, second) != 0
                || javan_object_equals(first, third) != 0
                || javan_object_equals(first, fourth) != 0
                || javan_object_equals(first, fifth) != 0
                || javan_object_equals(first, sixth) != 0
                || javan_object_equals(first, seventh) != 0
                || javan_object_equals(first, eighth) != 0
                || javan_object_equals(first, ninth) != 0
                || javan_object_equals(first, tenth) != 0
                || javan_object_equals(second, third) != 0
                || javan_object_equals(second, fourth) != 0
                || javan_object_equals(second, fifth) != 0
                || javan_object_equals(second, sixth) != 0
                || javan_object_equals(second, seventh) != 0
                || javan_object_equals(second, eighth) != 0
                || javan_object_equals(second, ninth) != 0
                || javan_object_equals(second, tenth) != 0
                || javan_object_equals(third, fourth) != 0
                || javan_object_equals(third, fifth) != 0
                || javan_object_equals(third, sixth) != 0
                || javan_object_equals(third, seventh) != 0
                || javan_object_equals(third, eighth) != 0
                || javan_object_equals(third, ninth) != 0
                || javan_object_equals(third, tenth) != 0
                || javan_object_equals(fourth, fifth) != 0
                || javan_object_equals(fourth, sixth) != 0
                || javan_object_equals(fourth, seventh) != 0
                || javan_object_equals(fourth, eighth) != 0
                || javan_object_equals(fourth, ninth) != 0
                || javan_object_equals(fourth, tenth) != 0
                || javan_object_equals(fifth, sixth) != 0
                || javan_object_equals(fifth, seventh) != 0
                || javan_object_equals(fifth, eighth) != 0
                || javan_object_equals(fifth, ninth) != 0
                || javan_object_equals(fifth, tenth) != 0
                || javan_object_equals(sixth, seventh) != 0
                || javan_object_equals(sixth, eighth) != 0
                || javan_object_equals(sixth, ninth) != 0
                || javan_object_equals(sixth, tenth) != 0
                || javan_object_equals(seventh, eighth) != 0
                || javan_object_equals(seventh, ninth) != 0
                || javan_object_equals(seventh, tenth) != 0
                || javan_object_equals(eighth, ninth) != 0
                || javan_object_equals(eighth, tenth) != 0
                || javan_object_equals(ninth, tenth) != 0) {
                javan_panic("duplicate Set.of element");
            }
            void* first_root = first;
            void* second_root = second;
            void* third_root = third;
            void* fourth_root = fourth;
            void* fifth_root = fifth;
            void* sixth_root = sixth;
            void* seventh_root = seventh;
            void* eighth_root = eighth;
            void* ninth_root = ninth;
            void* tenth_root = tenth;
            void** javan_set_of_decuple_roots[] = {
                (void**) &first_root,
                (void**) &second_root,
                (void**) &third_root,
                (void**) &fourth_root,
                (void**) &fifth_root,
                (void**) &sixth_root,
                (void**) &seventh_root,
                (void**) &eighth_root,
                (void**) &ninth_root,
                (void**) &tenth_root
            };
            javan_root_frame_push(javan_set_of_decuple_roots, 10);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            javan_set_add(set, first_root);
            javan_set_add(set, second_root);
            javan_set_add(set, third_root);
            javan_set_add(set, fourth_root);
            javan_set_add(set, fifth_root);
            javan_set_add(set, sixth_root);
            javan_set_add(set, seventh_root);
            javan_set_add(set, eighth_root);
            javan_set_add(set, ninth_root);
            javan_set_add(set, tenth_root);
            set->immutable = 1;
            javan_root_frame_pop(javan_set_of_decuple_roots);
            return set;
        }

        void* javan_set_of_array(void* array) {
            javan_array_header* header = javan_array_checked(array);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            javan_object_array* values = (javan_object_array*) header;
            void* result_value = NULL;
            void** javan_set_of_array_roots[] = {
                (void**) &values,
                (void**) &result_value
            };
            javan_root_frame_push(javan_set_of_array_roots, 2);
            result_value = javan_hashset_new();
            for (int index = 0; index < values->length; index++) {
                void* element = values->values[index];
                if (element == NULL) {
                    javan_panic("null Set.of element");
                }
                if (javan_set_add(result_value, element) == 0) {
                    javan_panic("duplicate Set.of element");
                }
            }
            ((javan_object_list*) result_value)->immutable = 1;
            javan_root_frame_pop(javan_set_of_array_roots);
            return result_value;
        }

        void* javan_set_singleton(void* value) {
            void* value_root = value;
            void** javan_set_singleton_roots[] = {
                (void**) &value_root
            };
            javan_root_frame_push(javan_set_singleton_roots, 1);
            javan_object_list* set = (javan_object_list*) javan_hashset_new();
            set->immutable = 1;
            javan_list_ensure_capacity(set, 1);
            set->values[0] = value_root;
            set->length = 1;
            javan_root_frame_pop(javan_set_singleton_roots);
            return set;
        }

        void* javan_set_unmodifiable(void* value) {
            javan_object_list* set = javan_list_checked(value);
            if (set->immutable != 0 && set->backing != NULL && (set->view_flags & JAVAN_LIST_VIEW_UNMODIFIABLE) != 0) {
                return set;
            }
            return javan_list_new_view(set, 1, JAVAN_LIST_VIEW_UNMODIFIABLE | JAVAN_LIST_VIEW_SET);
        }

        int javan_set_add(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            if (javan_list_contains(value, element) != 0) {
                return 0;
            }
            javan_list_append_raw(list, element);
            list->mod_count++;
            return 1;
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
            map->view_flags = 0;
            map->backing = NULL;
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

        static javan_object_map* javan_map_new_view(javan_object_map* backing, int immutable, int view_flags) {
            if (backing == NULL) {
                javan_panic("null map backing");
            }
            javan_object_map* map = NULL;
            void** roots[] = {
                (void**) &backing,
                (void**) &map
            };
            javan_root_frame_push(roots, 2);
            map = javan_map_new_with_capacity(0, immutable);
            map->view_flags = view_flags;
            map->backing = backing;
            map->length = 0;
            map->capacity = 0;
            javan_root_frame_pop(roots);
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

        static int javan_map_logical_length(javan_object_map* map) {
            if (map->backing != NULL) {
                return javan_map_logical_length(map->backing);
            }
            return map->length;
        }

        static int javan_map_observed_mod_count(javan_object_map* map) {
            if (map->backing != NULL) {
                return javan_map_observed_mod_count(map->backing);
            }
            return map->mod_count;
        }

        static void* javan_map_key_unchecked(javan_object_map* map, int index) {
            if (map->backing != NULL) {
                return javan_map_key_unchecked(map->backing, index);
            }
            return map->keys[index];
        }

        static void* javan_map_value_unchecked(javan_object_map* map, int index) {
            if (map->backing != NULL) {
                return javan_map_value_unchecked(map->backing, index);
            }
            return map->values[index];
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
            int length = javan_map_logical_length(map);
            for (int index = 0; index < length; index++) {
                if (javan_map_key_equals(javan_map_key_unchecked(map, index), key) != 0) {
                    return index;
                }
            }
            return -1;
        }
        """;

    private static final String SOURCE_COLLECTIONS_TAIL = """
        void* javan_hashmap_new(void) {
            return javan_map_new_with_capacity(0, 0);
        }

        static int javan_hashmap_capacity_for_expected_mappings(int num_mappings) {
            if (num_mappings < 0) {
                char buffer[64];
                snprintf(buffer, sizeof(buffer), "Negative number of mappings: %d", num_mappings);
                javan_panic(buffer);
            }
            if (num_mappings < 3) {
                return num_mappings + 1;
            }
            if (num_mappings < 1073741824) {
                return (int) (((float) num_mappings / 0.75f) + 1.0f);
            }
            return 2147483647;
        }

        void* javan_hashset_new_with_expected_elements(int num_elements) {
            if (num_elements < 0) {
                char buffer[64];
                snprintf(buffer, sizeof(buffer), "Negative number of elements: %d", num_elements);
                javan_panic(buffer);
            }
            return javan_list_new_with_capacity(javan_hashmap_capacity_for_expected_mappings(num_elements), 0);
        }

        void* javan_linkedhashset_new_with_expected_elements(int num_elements) {
            if (num_elements < 0) {
                char buffer[64];
                snprintf(buffer, sizeof(buffer), "Negative number of elements: %d", num_elements);
                javan_panic(buffer);
            }
            return javan_list_new_with_capacity(javan_hashmap_capacity_for_expected_mappings(num_elements), 0);
        }

        void javan_set_initialize_capacity(void* value, int capacity) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            if (capacity < 0) {
                char buffer[64];
                snprintf(buffer, sizeof(buffer), "Illegal initial capacity: %d", capacity);
                javan_panic(buffer);
            }
            if (capacity == 0) {
                return;
            }
            javan_list_ensure_capacity(list, capacity);
        }

        static void javan_format_java_float(float value, char* buffer, unsigned long capacity) {
            if (capacity == 0) {
                return;
            }
            if (isnan(value)) {
                snprintf(buffer, capacity, "NaN");
                return;
            }
            if (value == 0.0f) {
                if (signbit(value)) {
                    snprintf(buffer, capacity, "-0.0");
                } else {
                    snprintf(buffer, capacity, "0.0");
                }
                return;
            }
            snprintf(buffer, capacity, "%.9g", value);
            if (strchr(buffer, '.') == NULL && strchr(buffer, 'e') == NULL && strchr(buffer, 'E') == NULL) {
                unsigned long length = (unsigned long) strlen(buffer);
                if ((length + 3) <= capacity) {
                    buffer[length] = '.';
                    buffer[length + 1] = '0';
                    buffer[length + 2] = '\\0';
                }
            }
        }

        static void javan_panic_illegal_load_factor(float load_factor) {
            char load_factor_text[32];
            char buffer[64];
            javan_format_java_float(load_factor, load_factor_text, sizeof(load_factor_text));
            snprintf(buffer, sizeof(buffer), "Illegal load factor: %s", load_factor_text);
            javan_panic(buffer);
        }

        void javan_set_initialize_capacity_with_load_factor(void* value, int capacity, float load_factor) {
            if (!(load_factor > 0.0f)) {
                javan_panic_illegal_load_factor(load_factor);
            }
            javan_set_initialize_capacity(value, capacity);
        }

        void* javan_hashmap_new_with_expected_mappings(int num_mappings) {
            return javan_map_new_with_capacity(javan_hashmap_capacity_for_expected_mappings(num_mappings), 0);
        }

        void* javan_linkedhashmap_new_with_expected_mappings(int num_mappings) {
            return javan_map_new_with_capacity(javan_hashmap_capacity_for_expected_mappings(num_mappings), 0);
        }

        void javan_map_initialize_capacity(void* value, int capacity) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            if (capacity < 0) {
                javan_panic("negative map capacity");
            }
            if (capacity == 0) {
                return;
            }
            if (map->backing != NULL) {
                javan_panic("unsupported map backing for capacity initialization");
            }
            javan_map_ensure_capacity(map, capacity);
        }

        void javan_map_initialize_capacity_with_load_factor(void* value, int capacity, float load_factor) {
            if (!(load_factor > 0.0f)) {
                javan_panic("invalid map load factor");
            }
            javan_map_initialize_capacity(value, capacity);
        }

        void javan_map_initialize_capacity_with_load_factor_and_concurrency(
            void* value,
            int capacity,
            float load_factor,
            int concurrency_level
        ) {
            if (!(load_factor > 0.0f)) {
                javan_panic("invalid map load factor");
            }
            if (concurrency_level <= 0) {
                javan_panic("non-positive map concurrency level");
            }
            if (capacity < concurrency_level) {
                capacity = concurrency_level;
            }
            javan_map_initialize_capacity(value, capacity);
        }

        static void* javan_map_entry_alloc(void* key, void* value) {
            void* entry_value = javan_alloc(sizeof(javan_map_entry_state));
            javan_map_entry_state* entry = (javan_map_entry_state*) entry_value;
            entry->magic = JAVAN_MAP_ENTRY_MAGIC;
            entry->reserved0 = 0;
            entry->reserved1 = 0;
            entry->reserved2 = 0;
            entry->key = key;
            entry->value = value;
            javan_update_runtime_allocation_kind(entry_value, JAVAN_RUNTIME_KIND_MAP_ENTRY);
            return entry_value;
        }

        void* javan_map_entry_new(void* key, void* value) {
            if (key == NULL || value == NULL) {
                javan_panic("null Map.entry component");
            }
            void* key_root = key;
            void* value_root = value;
            void* entry_value = NULL;
            void** roots[] = {
                (void**) &key_root,
                (void**) &value_root,
                (void**) &entry_value
            };
            javan_root_frame_push(roots, 3);
            entry_value = javan_map_entry_alloc(key_root, value_root);
            javan_root_frame_pop(roots);
            return entry_value;
        }

        void* javan_map_empty(void) {
            return javan_map_new_with_capacity(0, 1);
        }

        void* javan_map_singleton(void* key, void* value) {
            void* key_root = key;
            void* value_root = value;
            void** javan_map_singleton_roots[] = {
                (void**) &key_root,
                (void**) &value_root
            };
            javan_root_frame_push(javan_map_singleton_roots, 2);
            javan_object_map* map = javan_map_new_with_capacity(1, 1);
            map->keys[0] = key_root;
            map->values[0] = value_root;
            map->length = 1;
            javan_root_frame_pop(javan_map_singleton_roots);
            return map;
        }

        void* javan_map_pair(void* first_key, void* first_value, void* second_key, void* second_value) {
            if (first_key == NULL || first_value == NULL || second_key == NULL || second_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void** javan_map_pair_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root
            };
            javan_root_frame_push(javan_map_pair_roots, 4);
            javan_object_map* map = javan_map_new_with_capacity(2, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->length = 2;
            javan_root_frame_pop(javan_map_pair_roots);
            return map;
        }

        void* javan_map_triple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value) {
            if (first_key == NULL || first_value == NULL || second_key == NULL || second_value == NULL || third_key == NULL || third_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(second_key, third_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void** javan_map_triple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root
            };
            javan_root_frame_push(javan_map_triple_roots, 6);
            javan_object_map* map = javan_map_new_with_capacity(3, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->length = 3;
            javan_root_frame_pop(javan_map_triple_roots);
            return map;
        }

        void* javan_map_quadruple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void** javan_map_quadruple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root
            };
            javan_root_frame_push(javan_map_quadruple_roots, 8);
            javan_object_map* map = javan_map_new_with_capacity(4, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->length = 4;
            javan_root_frame_pop(javan_map_quadruple_roots);
            return map;
        }

        void* javan_map_quintuple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value, void* fifth_key, void* fifth_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL
                || fifth_key == NULL || fifth_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(first_key, fifth_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(second_key, fifth_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0
                || javan_object_equals(third_key, fifth_key) != 0
                || javan_object_equals(fourth_key, fifth_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void* fifth_key_root = fifth_key;
            void* fifth_value_root = fifth_value;
            void** javan_map_quintuple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root,
                (void**) &fifth_key_root,
                (void**) &fifth_value_root
            };
            javan_root_frame_push(javan_map_quintuple_roots, 10);
            javan_object_map* map = javan_map_new_with_capacity(5, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->keys[4] = fifth_key_root;
            map->values[4] = fifth_value_root;
            map->length = 5;
            javan_root_frame_pop(javan_map_quintuple_roots);
            return map;
        }

        void* javan_map_sextuple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value, void* fifth_key, void* fifth_value, void* sixth_key, void* sixth_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL
                || fifth_key == NULL || fifth_value == NULL
                || sixth_key == NULL || sixth_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(first_key, fifth_key) != 0
                || javan_object_equals(first_key, sixth_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(second_key, fifth_key) != 0
                || javan_object_equals(second_key, sixth_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0
                || javan_object_equals(third_key, fifth_key) != 0
                || javan_object_equals(third_key, sixth_key) != 0
                || javan_object_equals(fourth_key, fifth_key) != 0
                || javan_object_equals(fourth_key, sixth_key) != 0
                || javan_object_equals(fifth_key, sixth_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void* fifth_key_root = fifth_key;
            void* fifth_value_root = fifth_value;
            void* sixth_key_root = sixth_key;
            void* sixth_value_root = sixth_value;
            void** javan_map_sextuple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root,
                (void**) &fifth_key_root,
                (void**) &fifth_value_root,
                (void**) &sixth_key_root,
                (void**) &sixth_value_root
            };
            javan_root_frame_push(javan_map_sextuple_roots, 12);
            javan_object_map* map = javan_map_new_with_capacity(6, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->keys[4] = fifth_key_root;
            map->values[4] = fifth_value_root;
            map->keys[5] = sixth_key_root;
            map->values[5] = sixth_value_root;
            map->length = 6;
            javan_root_frame_pop(javan_map_sextuple_roots);
            return map;
        }

        void* javan_map_septuple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value, void* fifth_key, void* fifth_value, void* sixth_key, void* sixth_value, void* seventh_key, void* seventh_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL
                || fifth_key == NULL || fifth_value == NULL
                || sixth_key == NULL || sixth_value == NULL
                || seventh_key == NULL || seventh_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(first_key, fifth_key) != 0
                || javan_object_equals(first_key, sixth_key) != 0
                || javan_object_equals(first_key, seventh_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(second_key, fifth_key) != 0
                || javan_object_equals(second_key, sixth_key) != 0
                || javan_object_equals(second_key, seventh_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0
                || javan_object_equals(third_key, fifth_key) != 0
                || javan_object_equals(third_key, sixth_key) != 0
                || javan_object_equals(third_key, seventh_key) != 0
                || javan_object_equals(fourth_key, fifth_key) != 0
                || javan_object_equals(fourth_key, sixth_key) != 0
                || javan_object_equals(fourth_key, seventh_key) != 0
                || javan_object_equals(fifth_key, sixth_key) != 0
                || javan_object_equals(fifth_key, seventh_key) != 0
                || javan_object_equals(sixth_key, seventh_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void* fifth_key_root = fifth_key;
            void* fifth_value_root = fifth_value;
            void* sixth_key_root = sixth_key;
            void* sixth_value_root = sixth_value;
            void* seventh_key_root = seventh_key;
            void* seventh_value_root = seventh_value;
            void** javan_map_septuple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root,
                (void**) &fifth_key_root,
                (void**) &fifth_value_root,
                (void**) &sixth_key_root,
                (void**) &sixth_value_root,
                (void**) &seventh_key_root,
                (void**) &seventh_value_root
            };
            javan_root_frame_push(javan_map_septuple_roots, 14);
            javan_object_map* map = javan_map_new_with_capacity(7, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->keys[4] = fifth_key_root;
            map->values[4] = fifth_value_root;
            map->keys[5] = sixth_key_root;
            map->values[5] = sixth_value_root;
            map->keys[6] = seventh_key_root;
            map->values[6] = seventh_value_root;
            map->length = 7;
            javan_root_frame_pop(javan_map_septuple_roots);
            return map;
        }

        void* javan_map_octuple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value, void* fifth_key, void* fifth_value, void* sixth_key, void* sixth_value, void* seventh_key, void* seventh_value, void* eighth_key, void* eighth_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL
                || fifth_key == NULL || fifth_value == NULL
                || sixth_key == NULL || sixth_value == NULL
                || seventh_key == NULL || seventh_value == NULL
                || eighth_key == NULL || eighth_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(first_key, fifth_key) != 0
                || javan_object_equals(first_key, sixth_key) != 0
                || javan_object_equals(first_key, seventh_key) != 0
                || javan_object_equals(first_key, eighth_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(second_key, fifth_key) != 0
                || javan_object_equals(second_key, sixth_key) != 0
                || javan_object_equals(second_key, seventh_key) != 0
                || javan_object_equals(second_key, eighth_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0
                || javan_object_equals(third_key, fifth_key) != 0
                || javan_object_equals(third_key, sixth_key) != 0
                || javan_object_equals(third_key, seventh_key) != 0
                || javan_object_equals(third_key, eighth_key) != 0
                || javan_object_equals(fourth_key, fifth_key) != 0
                || javan_object_equals(fourth_key, sixth_key) != 0
                || javan_object_equals(fourth_key, seventh_key) != 0
                || javan_object_equals(fourth_key, eighth_key) != 0
                || javan_object_equals(fifth_key, sixth_key) != 0
                || javan_object_equals(fifth_key, seventh_key) != 0
                || javan_object_equals(fifth_key, eighth_key) != 0
                || javan_object_equals(sixth_key, seventh_key) != 0
                || javan_object_equals(sixth_key, eighth_key) != 0
                || javan_object_equals(seventh_key, eighth_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void* fifth_key_root = fifth_key;
            void* fifth_value_root = fifth_value;
            void* sixth_key_root = sixth_key;
            void* sixth_value_root = sixth_value;
            void* seventh_key_root = seventh_key;
            void* seventh_value_root = seventh_value;
            void* eighth_key_root = eighth_key;
            void* eighth_value_root = eighth_value;
            void** javan_map_octuple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root,
                (void**) &fifth_key_root,
                (void**) &fifth_value_root,
                (void**) &sixth_key_root,
                (void**) &sixth_value_root,
                (void**) &seventh_key_root,
                (void**) &seventh_value_root,
                (void**) &eighth_key_root,
                (void**) &eighth_value_root
            };
            javan_root_frame_push(javan_map_octuple_roots, 16);
            javan_object_map* map = javan_map_new_with_capacity(8, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->keys[4] = fifth_key_root;
            map->values[4] = fifth_value_root;
            map->keys[5] = sixth_key_root;
            map->values[5] = sixth_value_root;
            map->keys[6] = seventh_key_root;
            map->values[6] = seventh_value_root;
            map->keys[7] = eighth_key_root;
            map->values[7] = eighth_value_root;
            map->length = 8;
            javan_root_frame_pop(javan_map_octuple_roots);
            return map;
        }

        void* javan_map_nonuple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value, void* fifth_key, void* fifth_value, void* sixth_key, void* sixth_value, void* seventh_key, void* seventh_value, void* eighth_key, void* eighth_value, void* ninth_key, void* ninth_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL
                || fifth_key == NULL || fifth_value == NULL
                || sixth_key == NULL || sixth_value == NULL
                || seventh_key == NULL || seventh_value == NULL
                || eighth_key == NULL || eighth_value == NULL
                || ninth_key == NULL || ninth_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(first_key, fifth_key) != 0
                || javan_object_equals(first_key, sixth_key) != 0
                || javan_object_equals(first_key, seventh_key) != 0
                || javan_object_equals(first_key, eighth_key) != 0
                || javan_object_equals(first_key, ninth_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(second_key, fifth_key) != 0
                || javan_object_equals(second_key, sixth_key) != 0
                || javan_object_equals(second_key, seventh_key) != 0
                || javan_object_equals(second_key, eighth_key) != 0
                || javan_object_equals(second_key, ninth_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0
                || javan_object_equals(third_key, fifth_key) != 0
                || javan_object_equals(third_key, sixth_key) != 0
                || javan_object_equals(third_key, seventh_key) != 0
                || javan_object_equals(third_key, eighth_key) != 0
                || javan_object_equals(third_key, ninth_key) != 0
                || javan_object_equals(fourth_key, fifth_key) != 0
                || javan_object_equals(fourth_key, sixth_key) != 0
                || javan_object_equals(fourth_key, seventh_key) != 0
                || javan_object_equals(fourth_key, eighth_key) != 0
                || javan_object_equals(fourth_key, ninth_key) != 0
                || javan_object_equals(fifth_key, sixth_key) != 0
                || javan_object_equals(fifth_key, seventh_key) != 0
                || javan_object_equals(fifth_key, eighth_key) != 0
                || javan_object_equals(fifth_key, ninth_key) != 0
                || javan_object_equals(sixth_key, seventh_key) != 0
                || javan_object_equals(sixth_key, eighth_key) != 0
                || javan_object_equals(sixth_key, ninth_key) != 0
                || javan_object_equals(seventh_key, eighth_key) != 0
                || javan_object_equals(seventh_key, ninth_key) != 0
                || javan_object_equals(eighth_key, ninth_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void* fifth_key_root = fifth_key;
            void* fifth_value_root = fifth_value;
            void* sixth_key_root = sixth_key;
            void* sixth_value_root = sixth_value;
            void* seventh_key_root = seventh_key;
            void* seventh_value_root = seventh_value;
            void* eighth_key_root = eighth_key;
            void* eighth_value_root = eighth_value;
            void* ninth_key_root = ninth_key;
            void* ninth_value_root = ninth_value;
            void** javan_map_nonuple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root,
                (void**) &fifth_key_root,
                (void**) &fifth_value_root,
                (void**) &sixth_key_root,
                (void**) &sixth_value_root,
                (void**) &seventh_key_root,
                (void**) &seventh_value_root,
                (void**) &eighth_key_root,
                (void**) &eighth_value_root,
                (void**) &ninth_key_root,
                (void**) &ninth_value_root
            };
            javan_root_frame_push(javan_map_nonuple_roots, 18);
            javan_object_map* map = javan_map_new_with_capacity(9, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->keys[4] = fifth_key_root;
            map->values[4] = fifth_value_root;
            map->keys[5] = sixth_key_root;
            map->values[5] = sixth_value_root;
            map->keys[6] = seventh_key_root;
            map->values[6] = seventh_value_root;
            map->keys[7] = eighth_key_root;
            map->values[7] = eighth_value_root;
            map->keys[8] = ninth_key_root;
            map->values[8] = ninth_value_root;
            map->length = 9;
            javan_root_frame_pop(javan_map_nonuple_roots);
            return map;
        }

        void* javan_map_decuple(void* first_key, void* first_value, void* second_key, void* second_value, void* third_key, void* third_value, void* fourth_key, void* fourth_value, void* fifth_key, void* fifth_value, void* sixth_key, void* sixth_value, void* seventh_key, void* seventh_value, void* eighth_key, void* eighth_value, void* ninth_key, void* ninth_value, void* tenth_key, void* tenth_value) {
            if (first_key == NULL || first_value == NULL
                || second_key == NULL || second_value == NULL
                || third_key == NULL || third_value == NULL
                || fourth_key == NULL || fourth_value == NULL
                || fifth_key == NULL || fifth_value == NULL
                || sixth_key == NULL || sixth_value == NULL
                || seventh_key == NULL || seventh_value == NULL
                || eighth_key == NULL || eighth_value == NULL
                || ninth_key == NULL || ninth_value == NULL
                || tenth_key == NULL || tenth_value == NULL) {
                javan_panic("null Map.of entry");
            }
            if (javan_object_equals(first_key, second_key) != 0
                || javan_object_equals(first_key, third_key) != 0
                || javan_object_equals(first_key, fourth_key) != 0
                || javan_object_equals(first_key, fifth_key) != 0
                || javan_object_equals(first_key, sixth_key) != 0
                || javan_object_equals(first_key, seventh_key) != 0
                || javan_object_equals(first_key, eighth_key) != 0
                || javan_object_equals(first_key, ninth_key) != 0
                || javan_object_equals(first_key, tenth_key) != 0
                || javan_object_equals(second_key, third_key) != 0
                || javan_object_equals(second_key, fourth_key) != 0
                || javan_object_equals(second_key, fifth_key) != 0
                || javan_object_equals(second_key, sixth_key) != 0
                || javan_object_equals(second_key, seventh_key) != 0
                || javan_object_equals(second_key, eighth_key) != 0
                || javan_object_equals(second_key, ninth_key) != 0
                || javan_object_equals(second_key, tenth_key) != 0
                || javan_object_equals(third_key, fourth_key) != 0
                || javan_object_equals(third_key, fifth_key) != 0
                || javan_object_equals(third_key, sixth_key) != 0
                || javan_object_equals(third_key, seventh_key) != 0
                || javan_object_equals(third_key, eighth_key) != 0
                || javan_object_equals(third_key, ninth_key) != 0
                || javan_object_equals(third_key, tenth_key) != 0
                || javan_object_equals(fourth_key, fifth_key) != 0
                || javan_object_equals(fourth_key, sixth_key) != 0
                || javan_object_equals(fourth_key, seventh_key) != 0
                || javan_object_equals(fourth_key, eighth_key) != 0
                || javan_object_equals(fourth_key, ninth_key) != 0
                || javan_object_equals(fourth_key, tenth_key) != 0
                || javan_object_equals(fifth_key, sixth_key) != 0
                || javan_object_equals(fifth_key, seventh_key) != 0
                || javan_object_equals(fifth_key, eighth_key) != 0
                || javan_object_equals(fifth_key, ninth_key) != 0
                || javan_object_equals(fifth_key, tenth_key) != 0
                || javan_object_equals(sixth_key, seventh_key) != 0
                || javan_object_equals(sixth_key, eighth_key) != 0
                || javan_object_equals(sixth_key, ninth_key) != 0
                || javan_object_equals(sixth_key, tenth_key) != 0
                || javan_object_equals(seventh_key, eighth_key) != 0
                || javan_object_equals(seventh_key, ninth_key) != 0
                || javan_object_equals(seventh_key, tenth_key) != 0
                || javan_object_equals(eighth_key, ninth_key) != 0
                || javan_object_equals(eighth_key, tenth_key) != 0
                || javan_object_equals(ninth_key, tenth_key) != 0) {
                javan_panic("duplicate Map.of key");
            }
            void* first_key_root = first_key;
            void* first_value_root = first_value;
            void* second_key_root = second_key;
            void* second_value_root = second_value;
            void* third_key_root = third_key;
            void* third_value_root = third_value;
            void* fourth_key_root = fourth_key;
            void* fourth_value_root = fourth_value;
            void* fifth_key_root = fifth_key;
            void* fifth_value_root = fifth_value;
            void* sixth_key_root = sixth_key;
            void* sixth_value_root = sixth_value;
            void* seventh_key_root = seventh_key;
            void* seventh_value_root = seventh_value;
            void* eighth_key_root = eighth_key;
            void* eighth_value_root = eighth_value;
            void* ninth_key_root = ninth_key;
            void* ninth_value_root = ninth_value;
            void* tenth_key_root = tenth_key;
            void* tenth_value_root = tenth_value;
            void** javan_map_decuple_roots[] = {
                (void**) &first_key_root,
                (void**) &first_value_root,
                (void**) &second_key_root,
                (void**) &second_value_root,
                (void**) &third_key_root,
                (void**) &third_value_root,
                (void**) &fourth_key_root,
                (void**) &fourth_value_root,
                (void**) &fifth_key_root,
                (void**) &fifth_value_root,
                (void**) &sixth_key_root,
                (void**) &sixth_value_root,
                (void**) &seventh_key_root,
                (void**) &seventh_value_root,
                (void**) &eighth_key_root,
                (void**) &eighth_value_root,
                (void**) &ninth_key_root,
                (void**) &ninth_value_root,
                (void**) &tenth_key_root,
                (void**) &tenth_value_root
            };
            javan_root_frame_push(javan_map_decuple_roots, 20);
            javan_object_map* map = javan_map_new_with_capacity(10, 1);
            map->keys[0] = first_key_root;
            map->values[0] = first_value_root;
            map->keys[1] = second_key_root;
            map->values[1] = second_value_root;
            map->keys[2] = third_key_root;
            map->values[2] = third_value_root;
            map->keys[3] = fourth_key_root;
            map->values[3] = fourth_value_root;
            map->keys[4] = fifth_key_root;
            map->values[4] = fifth_value_root;
            map->keys[5] = sixth_key_root;
            map->values[5] = sixth_value_root;
            map->keys[6] = seventh_key_root;
            map->values[6] = seventh_value_root;
            map->keys[7] = eighth_key_root;
            map->values[7] = eighth_value_root;
            map->keys[8] = ninth_key_root;
            map->values[8] = ninth_value_root;
            map->keys[9] = tenth_key_root;
            map->values[9] = tenth_value_root;
            map->length = 10;
            javan_root_frame_pop(javan_map_decuple_roots);
            return map;
        }

        void* javan_map_of_entries(void* value) {
            javan_array_header* header = javan_array_checked(value);
            javan_array_kind_checked(header, JAVAN_ARRAY_KIND_OBJECT);
            javan_object_array* entries = (javan_object_array*) header;
            void* result_value = NULL;
            void** javan_map_of_entries_roots[] = {
                (void**) &entries,
                (void**) &result_value
            };
            javan_root_frame_push(javan_map_of_entries_roots, 2);
            result_value = javan_map_new_with_capacity(entries->length, 1);
            javan_object_map* result = (javan_object_map*) result_value;
            for (int index = 0; index < entries->length; index++) {
                void* entry_value = entries->values[index];
                if (entry_value == NULL) {
                    javan_panic("null Map.ofEntries entry");
                }
                javan_map_entry_state* entry = javan_map_entry_checked(entry_value);
                if (entry->key == NULL || entry->value == NULL) {
                    javan_panic("null Map.of entry");
                }
                if (javan_map_find(result, entry->key) >= 0) {
                    javan_panic("duplicate Map.of key");
                }
                result->keys[index] = entry->key;
                result->values[index] = entry->value;
                result->length = index + 1;
            }
            javan_root_frame_pop(javan_map_of_entries_roots);
            return result;
        }

        void* javan_map_copy_of(void* value) {
            javan_object_map* source = javan_map_checked(value);
            void** javan_map_copy_roots[] = {
                (void**) &source
            };
            javan_root_frame_push(javan_map_copy_roots, 1);
            int length = javan_map_logical_length(source);
            javan_object_map* result = javan_map_new_with_capacity(length, 1);
            for (int index = 0; index < length; index++) {
                result->keys[index] = javan_map_key_unchecked(source, index);
                result->values[index] = javan_map_value_unchecked(source, index);
            }
            result->length = length;
            javan_root_frame_pop(javan_map_copy_roots);
            return result;
        }

        void javan_map_put_all(void* value, void* source_value) {
            javan_object_map* map = javan_map_checked(value);
            javan_object_map* source = javan_map_checked(source_value);
            javan_map_mutable_checked(map);
            void** javan_map_put_all_roots[] = {
                (void**) &map,
                (void**) &source
            };
            javan_root_frame_push(javan_map_put_all_roots, 2);
            int length = javan_map_logical_length(source);
            javan_map_ensure_capacity(map, map->length + length);
            for (int index = 0; index < length; index++) {
                void* key = javan_map_key_unchecked(source, index);
                void* element = javan_map_value_unchecked(source, index);
                javan_map_put(map, key, element);
            }
            javan_root_frame_pop(javan_map_put_all_roots);
        }

        void* javan_map_unmodifiable(void* value) {
            javan_object_map* map = javan_map_checked(value);
            if (map->immutable != 0 && map->backing != NULL && (map->view_flags & JAVAN_MAP_VIEW_UNMODIFIABLE) != 0) {
                return map;
            }
            return javan_map_new_view(map, 1, JAVAN_MAP_VIEW_UNMODIFIABLE);
        }

        void* javan_map_get(void* value, void* key) {
            javan_object_map* map = javan_map_checked(value);
            int index = javan_map_find(map, key);
            return index < 0 ? NULL : javan_map_value_unchecked(map, index);
        }

        void* javan_map_get_or_default(void* value, void* key, void* fallback) {
            javan_object_map* map = javan_map_checked(value);
            int index = javan_map_find(map, key);
            return index < 0 ? fallback : javan_map_value_unchecked(map, index);
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

        void* javan_map_replace(void* value, void* key, void* element) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            int index = javan_map_find(map, key);
            if (index < 0) {
                return NULL;
            }
            void* previous = javan_map_value_unchecked(map, index);
            if (map->backing != NULL) {
                map->backing->values[index] = element;
                return previous;
            }
            map->values[index] = element;
            return previous;
        }

        int javan_map_replace_entry(void* value, void* key, void* expected_value, void* new_value) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            int index = javan_map_find(map, key);
            if (index < 0) {
                return 0;
            }
            if (javan_object_equals(javan_map_value_unchecked(map, index), expected_value) == 0) {
                return 0;
            }
            if (map->backing != NULL) {
                map->backing->values[index] = new_value;
                return 1;
            }
            map->values[index] = new_value;
            return 1;
        }

        void javan_map_clear(void* value) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            if (map->backing != NULL) {
                javan_map_clear((void*) map->backing);
                return;
            }
            if (map->length == 0) {
                return;
            }
            for (int index = 0; index < map->length; index++) {
                map->keys[index] = NULL;
                map->values[index] = NULL;
            }
            map->length = 0;
            map->mod_count++;
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

        int javan_map_remove_entry(void* value, void* key, void* expected_value) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
            int index = javan_map_find(map, key);
            if (index < 0) {
                return 0;
            }
            if (javan_object_equals(javan_map_value_unchecked(map, index), expected_value) == 0) {
                return 0;
            }
            for (int cursor = index + 1; cursor < map->length; cursor++) {
                map->keys[cursor - 1] = map->keys[cursor];
                map->values[cursor - 1] = map->values[cursor];
            }
            map->length--;
            map->keys[map->length] = NULL;
            map->values[map->length] = NULL;
            map->mod_count++;
            return 1;
        }

        int javan_map_contains_key(void* value, void* key) {
            return javan_map_find(javan_map_checked(value), key) >= 0;
        }

        int javan_map_contains_value(void* value, void* expected_value) {
            javan_object_map* map = javan_map_checked(value);
            int length = javan_map_logical_length(map);
            for (int index = 0; index < length; index++) {
                if (javan_object_equals(javan_map_value_unchecked(map, index), expected_value) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        int javan_map_size(void* value) {
            return javan_map_logical_length(javan_map_checked(value));
        }

        int javan_map_is_empty(void* value) {
            return javan_map_logical_length(javan_map_checked(value)) == 0;
        }

        void* javan_map_key_set(void* value) {
            javan_object_map* map = javan_map_checked(value);
            void* set_value = NULL;
            void** javan_map_key_set_roots[] = {
                (void**) &map,
                (void**) &set_value
            };
            javan_root_frame_push(javan_map_key_set_roots, 2);
            set_value = javan_hashset_new();
            javan_object_list* set = (javan_object_list*) set_value;
            int length = javan_map_logical_length(map);
            for (int index = 0; index < length; index++) {
                javan_set_add(set, javan_map_key_unchecked(map, index));
            }
            javan_root_frame_pop(javan_map_key_set_roots);
            return set_value;
        }

        void* javan_map_entry_set(void* value) {
            javan_object_map* map = javan_map_checked(value);
            void* map_root = map;
            void* list_value = NULL;
            void* entry_value = NULL;
            void** roots[] = {
                (void**) &map_root,
                (void**) &list_value,
                (void**) &entry_value
            };
            javan_root_frame_push(roots, 3);
            int length = javan_map_logical_length(map);
            list_value = javan_list_new_with_capacity(length, 1);
            for (int index = 0; index < length; index++) {
                entry_value = javan_map_entry_alloc(javan_map_key_unchecked(map, index), javan_map_value_unchecked(map, index));
                javan_list_append_raw((javan_object_list*) list_value, entry_value);
                entry_value = NULL;
            }
            javan_root_frame_pop(roots);
            return list_value;
        }

        void* javan_map_entry_get_key(void* value) {
            return javan_map_entry_checked(value)->key;
        }

        void* javan_map_entry_get_value(void* value) {
            return javan_map_entry_checked(value)->value;
        }

        void* javan_map_values(void* value) {
            javan_object_map* map = javan_map_checked(value);
            void** javan_map_values_roots[] = {
                (void**) &map
            };
            javan_root_frame_push(javan_map_values_roots, 1);
            int length = javan_map_logical_length(map);
            javan_object_list* list = javan_list_new_with_capacity(length, 1);
            for (int index = 0; index < length; index++) {
                javan_list_append_raw(list, javan_map_value_unchecked(map, index));
            }
            javan_root_frame_pop(javan_map_values_roots);
            return list;
        }

        void* javan_materialized_lambda_new(int target_id) {
            if (target_id <= 0) {
                javan_panic("invalid materialized lambda target");
            }
            void* object_value = NULL;
            void* state_value = NULL;
            void** roots[] = {
                (void**) &object_value,
                (void**) &state_value
            };
            javan_root_frame_push(roots, 2);
            object_value = javan_alloc(sizeof(struct javan_object_header));
            struct javan_object_header* header = (struct javan_object_header*) object_value;
            header->_javan_type_id = 0;
            header->_javan_runtime_state = NULL;
            header->_javan_runtime_kind = JAVAN_RUNTIME_KIND_NONE;
            header->_javan_runtime_reserved = 0;
            state_value = javan_alloc(sizeof(javan_materialized_lambda_state));
            javan_materialized_lambda_state* state = (javan_materialized_lambda_state*) state_value;
            state->magic = JAVAN_MATERIALIZED_LAMBDA_MAGIC;
            state->target_id = target_id;
            state->capture_count = 0;
            state->captures = NULL;
            javan_update_runtime_allocation_kind(state_value, JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA);
            header->_javan_runtime_state = state_value;
            header->_javan_runtime_kind = JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA;
            header->_javan_runtime_reserved = 0;
            javan_update_runtime_allocation_kind(object_value, JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA);
            javan_root_frame_pop(roots);
            return object_value;
        }

        static int javan_materialized_lambda_capture_allocation_size(
            int capture_count,
            unsigned long* size
        ) {
            if (size == NULL
                || capture_count < 0
                || capture_count > JAVAN_MATERIALIZED_LAMBDA_MAX_CAPTURES) {
                return 0;
            }
            unsigned long count = (unsigned long) capture_count;
            if (count > ULONG_MAX / sizeof(void*)) {
                return 0;
            }
            *size = count * sizeof(void*);
            return 1;
        }

        void* javan_materialized_lambda_new_with_captures(int target_id, int capture_count, ...) {
            unsigned long captures_size = 0;
            if (javan_materialized_lambda_capture_allocation_size(capture_count, &captures_size) == 0) {
                javan_panic("invalid materialized lambda capture count");
            }
            javan_check_allocation_size(captures_size);
            void* capture_values[capture_count > 0 ? capture_count : 1];
            void** capture_roots[capture_count > 0 ? capture_count : 1];
            va_list args;
            va_start(args, capture_count);
            for (int index = 0; index < capture_count; index++) {
                capture_values[index] = va_arg(args, void*);
                capture_roots[index] = &capture_values[index];
            }
            va_end(args);
            if (capture_count > 0) {
                javan_root_frame_push(capture_roots, capture_count);
            }
            void* object_value = NULL;
            void* state_value = NULL;
            void* captures_value = NULL;
            void** roots[] = {
                (void**) &object_value,
                (void**) &state_value,
                (void**) &captures_value
            };
            javan_root_frame_push(roots, 3);
            object_value = javan_materialized_lambda_new(target_id);
            struct javan_object_header* header = (struct javan_object_header*) object_value;
            state_value = header->_javan_runtime_state;
            javan_materialized_lambda_state* state = (javan_materialized_lambda_state*) state_value;
            if (capture_count > 0) {
                captures_value = javan_alloc(captures_size);
                javan_update_runtime_allocation_kind(captures_value, JAVAN_RUNTIME_KIND_OWNED_BUFFER);
                state->captures = (void**) captures_value;
                for (int index = 0; index < capture_count; index++) {
                    state->captures[index] = capture_values[index];
                }
            }
            state->capture_count = capture_count;
            javan_root_frame_pop(roots);
            if (capture_count > 0) {
                javan_root_frame_pop(capture_roots);
            }
            return object_value;
        }

        static javan_materialized_lambda_state* javan_materialized_lambda_state_node_unlocked(void* value) {
            javan_allocation_node* state_node = javan_find_allocation(value, NULL);
            if (state_node == NULL
                || state_node->kind != JAVAN_HEAP_KIND_RUNTIME
                || state_node->runtime_kind != JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA
                || state_node->size < sizeof(javan_materialized_lambda_state)) {
                return NULL;
            }
            javan_materialized_lambda_state* state = (javan_materialized_lambda_state*) value;
            unsigned long captures_size = 0;
            if (state->magic != JAVAN_MATERIALIZED_LAMBDA_MAGIC
                || state->target_id <= 0
                || javan_materialized_lambda_capture_allocation_size(
                    state->capture_count,
                    &captures_size
                ) == 0
                || (state->capture_count == 0 && state->captures != NULL)
                || (state->capture_count > 0 && state->captures == NULL)) {
                return NULL;
            }
            if (state->capture_count > 0) {
                javan_allocation_node* captures_node = javan_find_allocation((void*) state->captures, NULL);
                if (captures_node == NULL
                    || captures_node->kind != JAVAN_HEAP_KIND_RUNTIME
                    || captures_node->runtime_kind != JAVAN_RUNTIME_KIND_OWNED_BUFFER
                    || captures_node->size < captures_size) {
                    return NULL;
                }
            }
            return state;
        }

        static javan_materialized_lambda_state* javan_materialized_lambda_wrapper_state_unlocked(void* value) {
            javan_allocation_node* object_node = javan_find_allocation(value, NULL);
            if (object_node == NULL
                || object_node->kind != JAVAN_HEAP_KIND_RUNTIME
                || object_node->runtime_kind != JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA
                || object_node->size < sizeof(struct javan_object_header)) {
                return NULL;
            }
            struct javan_object_header* header = (struct javan_object_header*) value;
            if (header->_javan_type_id != 0
                || header->_javan_runtime_kind != JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA
                || header->_javan_runtime_state == NULL) {
                return NULL;
            }
            return javan_materialized_lambda_state_node_unlocked(header->_javan_runtime_state);
        }

        static int javan_materialized_lambda_is_instance_unlocked(void* value) {
            return javan_materialized_lambda_wrapper_state_unlocked(value) != NULL;
        }

        int javan_materialized_lambda_is_instance(void* value) {
            javan_runtime_lock_enter();
            int result = javan_materialized_lambda_is_instance_unlocked(value);
            javan_runtime_lock_leave();
            return result;
        }

        int javan_materialized_lambda_target_id(void* value) {
            javan_runtime_lock_enter();
            javan_materialized_lambda_state* state =
                javan_materialized_lambda_wrapper_state_unlocked(value);
            if (state == NULL) {
                javan_runtime_lock_leave();
                javan_panic("invalid materialized lambda target");
            }
            int target_id = state->target_id;
            javan_runtime_lock_leave();
            return target_id;
        }

        void* javan_materialized_lambda_capture(void* value, int capture_index) {
            javan_runtime_lock_enter();
            javan_materialized_lambda_state* state =
                javan_materialized_lambda_wrapper_state_unlocked(value);
            if (state == NULL
                || capture_index < 0
                || capture_index >= state->capture_count) {
                javan_runtime_lock_leave();
                javan_panic("invalid materialized lambda capture");
            }
            void* capture = state->captures[capture_index];
            javan_runtime_lock_leave();
            return capture;
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

    private static final String SOURCE_HEAP_ATOMIC_INTEGER = """
        void* javan_atomic_integer_new(void) {
            void* value = javan_alloc(sizeof(javan_atomic_integer_state));
            javan_atomic_integer_state* state = (javan_atomic_integer_state*) value;
            state->magic = JAVAN_ATOMIC_INTEGER_MAGIC;
            state->value = 0;
            state->reserved0 = 0;
            javan_update_runtime_allocation_kind(value, JAVAN_RUNTIME_KIND_ATOMIC_INTEGER);
            return value;
        }

        void javan_atomic_integer_init(void* value, int initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_integer_state* state = javan_atomic_integer_checked(value);
            state->value = initial_value;
            javan_runtime_lock_leave();
        }

        int javan_atomic_integer_get(void* value) {
            javan_runtime_lock_enter();
            int result = javan_atomic_integer_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        void javan_atomic_integer_set(void* value, int next_value) {
            javan_runtime_lock_enter();
            javan_atomic_integer_checked(value)->value = next_value;
            javan_runtime_lock_leave();
        }

        int javan_atomic_integer_get_and_increment(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_integer_state* state = javan_atomic_integer_checked(value);
            int current = state->value;
            state->value = current == INT_MAX ? INT_MIN : current + 1;
            javan_runtime_lock_leave();
            return current;
        }

        int javan_atomic_integer_increment_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_integer_state* state = javan_atomic_integer_checked(value);
            state->value = state->value == INT_MAX ? INT_MIN : state->value + 1;
            int result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        int javan_atomic_integer_decrement_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_integer_state* state = javan_atomic_integer_checked(value);
            state->value = state->value == INT_MIN ? INT_MAX : state->value - 1;
            int result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        void* javan_atomic_boolean_new(void) {
            void* value = javan_alloc(sizeof(javan_atomic_boolean_state));
            javan_atomic_boolean_state* state = (javan_atomic_boolean_state*) value;
            state->magic = JAVAN_ATOMIC_BOOLEAN_MAGIC;
            state->value = 0;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_update_runtime_allocation_kind(value, JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN);
            return value;
        }

        void javan_atomic_boolean_init(void* value, int initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_boolean_state* state = javan_atomic_boolean_checked(value);
            state->value = initial_value == 0 ? 0 : 1;
            javan_runtime_lock_leave();
        }

        int javan_atomic_boolean_get(void* value) {
            javan_runtime_lock_enter();
            int result = javan_atomic_boolean_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        void javan_atomic_boolean_set(void* value, int next_value) {
            javan_runtime_lock_enter();
            javan_atomic_boolean_checked(value)->value = next_value == 0 ? 0 : 1;
            javan_runtime_lock_leave();
        }

        void* javan_atomic_reference_new(void) {
            void* value = javan_alloc(sizeof(javan_atomic_reference_state));
            javan_atomic_reference_state* state = (javan_atomic_reference_state*) value;
            state->magic = JAVAN_ATOMIC_REFERENCE_MAGIC;
            state->reserved0 = 0;
            state->value = NULL;
            javan_update_runtime_allocation_kind(value, JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE);
            return value;
        }

        void javan_atomic_reference_init(void* value, void* initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference_state* state = javan_atomic_reference_checked(value);
            state->value = initial_value;
            javan_runtime_lock_leave();
        }

        void* javan_atomic_reference_get(void* value) {
            javan_runtime_lock_enter();
            void* result = javan_atomic_reference_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        int javan_atomic_reference_compare_and_set(void* value, void* expected_value, void* next_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference_state* state = javan_atomic_reference_checked(value);
            int result = 0;
            if (state->value == expected_value) {
                state->value = next_value;
                result = 1;
            }
            javan_runtime_lock_leave();
            return result;
        }

        void javan_atomic_reference_set(void* value, void* next_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference_state* state = javan_atomic_reference_checked(value);
            state->value = next_value;
            javan_runtime_lock_leave();
        }

        void javan_atomic_long_init(void* value, long long initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_long_state* state = javan_atomic_long_checked(value);
            state->value = initial_value;
            javan_runtime_lock_leave();
        }

        long long javan_atomic_long_get(void* value) {
            javan_runtime_lock_enter();
            long long result = javan_atomic_long_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        void javan_atomic_long_set(void* value, long long next_value) {
            javan_runtime_lock_enter();
            javan_atomic_long_checked(value)->value = next_value;
            javan_runtime_lock_leave();
        }

        long long javan_atomic_long_increment_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_long_state* state = javan_atomic_long_checked(value);
            state->value = state->value == LLONG_MAX ? LLONG_MIN : state->value + 1LL;
            long long result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        long long javan_atomic_long_decrement_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_long_state* state = javan_atomic_long_checked(value);
            state->value = state->value == LLONG_MIN ? LLONG_MAX : state->value - 1LL;
            long long result = state->value;
            javan_runtime_lock_leave();
            return result;
        }
        """;

    private static final String SOURCE_C_ABI_OBJECT_HANDLES = """
        static JavanObjectHandle* javan_object_handle_checked(JavanObjectHandle* handle) {
            if (handle == NULL) {
                return NULL;
            }
            JavanObjectHandle* current = javan_object_handles;
            while (current != NULL) {
                if (current == handle) {
                    return handle;
                }
                current = current->next;
            }
            javan_panic("invalid Javan object handle");
            return NULL;
        }

        JavanObjectHandle* javan_object_handle_new(void* value) {
            if (value == NULL) {
                return NULL;
            }
            JavanObjectHandle* handle = (JavanObjectHandle*) calloc(1, sizeof(JavanObjectHandle));
            if (handle == NULL) {
                javan_panic("out of memory creating Javan object handle");
            }
            handle->value = value;
            handle->references = 1;
            javan_runtime_lock_enter();
            handle->next = javan_object_handles;
            javan_object_handles = handle;
            javan_runtime_lock_leave();
            return handle;
        }

        void* javan_object_handle_value(JavanObjectHandle* handle) {
            if (handle == NULL) {
                return NULL;
            }
            javan_runtime_lock_enter();
            void* value = javan_object_handle_checked(handle)->value;
            javan_runtime_lock_leave();
            return value;
        }

        void javan_object_handle_retain(JavanObjectHandle* handle) {
            if (handle == NULL) {
                return;
            }
            javan_runtime_lock_enter();
            JavanObjectHandle* checked = javan_object_handle_checked(handle);
            if (checked->references == 0) {
                javan_panic("Javan object handle reference underflow");
            }
            checked->references++;
            javan_runtime_lock_leave();
        }

        void javan_object_handle_release(JavanObjectHandle* handle) {
            if (handle == NULL) {
                return;
            }
            javan_runtime_lock_enter();
            JavanObjectHandle* checked = javan_object_handle_checked(handle);
            if (checked->references == 0) {
                javan_panic("Javan object handle reference underflow");
            }
            checked->references--;
            if (checked->references == 0) {
                JavanObjectHandle** cursor = &javan_object_handles;
                while (*cursor != checked) {
                    cursor = &(*cursor)->next;
                }
                *cursor = checked->next;
                free(checked);
            }
            javan_runtime_lock_leave();
        }

        static void javan_object_handle_cleanup_all(void) {
            JavanObjectHandle* current = javan_object_handles;
            javan_object_handles = NULL;
            while (current != NULL) {
                JavanObjectHandle* next = current->next;
                free(current);
                current = next;
            }
        }

        static void javan_gc_mark_object_handles(void) {
            for (JavanObjectHandle* current = javan_object_handles; current != NULL; current = current->next) {
                javan_gc_mark_value(current->value);
            }
        }
        """;

    private RuntimeSourceMemorySections() {
    }

    static String heap() {
        String result = SOURCE_HEAP_HEAD;
        result = result + SOURCE_HEAP_TAIL_A;
        return result;
    }

    static String heapAlloc() {
        String result = SOURCE_HEAP_ALLOC_HEAD;
        result = result + SOURCE_HEAP_TAIL_B;
        result = result + SOURCE_HEAP_TAIL_C;
        result = result + SOURCE_HEAP_ALLOC_EXECUTOR;
        result = result + SOURCE_HEAP_ALLOC_DATE_TIME;
        result = result + SOURCE_HEAP_ALLOC_TAIL;
        result = result + SOURCE_HEAP_ALLOC_SCHEDULE_FIXED_DELAY;
        result = result + SOURCE_HEAP_ALLOC_TAIL_CONTINUED;
        result = result + SOURCE_HEAP_ATOMIC_INTEGER;
        return result;
    }

    static String arrays() {
        return SOURCE_ARRAYS;
    }

    static String collections() {
        String result = SOURCE_COLLECTIONS_HEAD;
        result = result + SOURCE_RECORD_SHAPES;
        result = result + SOURCE_COLLECTIONS_HEAD_CONTINUED;
        result = result + SOURCE_COLLECTIONS_TAIL;
        result = result + SOURCE_C_ABI_OBJECT_HANDLES;
        return result;
    }
}
