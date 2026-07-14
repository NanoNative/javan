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
        #define JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN 26
        #define JAVAN_RUNTIME_KIND_ATOMIC_INTEGER 27
        #define JAVAN_RUNTIME_KIND_ATOMIC_LONG 28
        #define JAVAN_RUNTIME_KIND_THROWABLE 29
        #define JAVAN_RUNTIME_KIND_LOCALE 30
        #define JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER 31
        #define JAVAN_RUNTIME_KIND_DATETIME_FORMATTER 32
        #define JAVAN_RUNTIME_KIND_ZONE_ID 33
        #define JAVAN_RUNTIME_KIND_INSTANT 34
        #define JAVAN_RUNTIME_KIND_DATE 35
        #define JAVAN_RUNTIME_KIND_LOCAL_DATE 36
        #define JAVAN_RUNTIME_KIND_LOCAL_TIME 37
        #define JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME 38
        #define JAVAN_RUNTIME_KIND_ZONED_DATE_TIME 39
        #define JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE 40
        #define JAVAN_RUNTIME_KIND_OPTIONAL_INT 41
        #define JAVAN_RUNTIME_KIND_RUNTIME 42
        #define JAVAN_RUNTIME_KIND_THREAD_MXBEAN 43
        #define JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN 44
        #define JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN 45
        #define JAVAN_RUNTIME_KIND_OBJECT_SET 46
        #define JAVAN_RUNTIME_KIND_SQL_DATE 47
        #define JAVAN_RUNTIME_KIND_SQL_TIME 48
        #define JAVAN_RUNTIME_KIND_SQL_TIMESTAMP 49
        #define JAVAN_RUNTIME_KIND_PROCESS_HANDLE 50
        #define JAVAN_RUNTIME_KIND_MEMORY_MXBEAN 51
        #define JAVAN_RUNTIME_KIND_MEMORY_USAGE 52
        #define JAVAN_RUNTIME_KIND_HTTP_EXCHANGE 53
        #define JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM 54
        #define JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME 55
        #define JAVAN_RUNTIME_KIND_CALENDAR 56
        #define JAVAN_RUNTIME_KIND_LOGGING_LEVEL 57
        #define JAVAN_RUNTIME_KIND_SIMPLE_DATE_FORMAT 58
        #define JAVAN_RUNTIME_KIND_UUID 59
        #define JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM 60
        #define JAVAN_RUNTIME_KIND_HTTP_SERVER 61
        #define JAVAN_RUNTIME_KIND_HTTP_CONTEXT 62
        #define JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH 63
        #define JAVAN_RUNTIME_KIND_FUTURE 64
        #define JAVAN_RUNTIME_KIND_THREAD_INFO 65
        #define JAVAN_LIST_VIEW_UNMODIFIABLE 1
        #define JAVAN_LIST_VIEW_REVERSED 2

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
            int present;
            int value;
            int reserved0;
            int reserved1;
        } javan_optional_int;

        typedef struct {
            int magic;
            int value;
            int reserved0;
            int reserved1;
        } javan_atomic_boolean;

        typedef struct {
            int magic;
            int value;
            int reserved0;
            int reserved1;
        } javan_atomic_integer;

        typedef struct {
            int magic;
            int reserved0;
            long long value;
        } javan_atomic_long;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            void* value;
        } javan_atomic_reference;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            const char* binary_name;
            void* message;
            javan_object_list* suppressed;
            void* stack_trace;
        } javan_throwable_value;

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
            int type_id;
            int is_enum;
            int reserved0;
            const char* binary_name;
        } javan_runtime_class_state;

        typedef struct {
            int magic;
            int shutdown_hook_runner_registered;
            int shutdown_hook_running;
            int reserved0;
            javan_object_list* shutdown_hooks;
        } javan_runtime_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
        } javan_thread_mxbean_value;

        typedef struct {
            int magic;
            int count;
            int reserved0;
            int reserved1;
        } javan_count_down_latch_value;

        typedef struct {
            int magic;
            int cancelled;
            int reserved0;
            int reserved1;
            void* thread;
        } javan_future_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long thread_id;
            void* thread_name;
            void* lock_name;
            void* lock_owner_name;
        } javan_thread_info_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
        } javan_runtime_mxbean_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
        } javan_operating_system_mxbean_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
        } javan_memory_mxbean_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long used;
            long long committed;
            long long max;
        } javan_memory_usage_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long pid;
        } javan_process_handle_value;

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

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            javan_uri_value* request_uri;
            char* request_method;
            javan_object_map* request_headers;
            void* request_body;
            int response_status_code;
            int response_headers_sent;
            int reserved3;
            int reserved4;
            long long response_content_length;
            javan_object_map* response_headers;
            void* response_body;
        } javan_http_exchange_value;

        typedef struct {
            int magic;
            int offset;
            int closed;
            int reserved0;
            void* bytes;
        } javan_http_input_stream_value;

        typedef struct {
            int magic;
            int closed;
            int reserved0;
            int reserved1;
            javan_http_exchange_value* exchange;
        } javan_http_output_stream_value;

        typedef struct {
            int magic;
            int started;
            int stop_requested;
            int completed;
            int backlog;
            int reserved0;
            javan_inet_socket_address* address;
            javan_server_socket* server_socket;
            javan_object_map* contexts;
            void* executor;
            void* native_handle;
        } javan_http_server_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            javan_http_server_value* server;
            char* path;
            void* handler;
        } javan_http_context_value;

        typedef struct {
            int magic;
            int kind;
            int reserved0;
            int reserved1;
        } javan_locale_value;

        typedef struct {
            int magic;
            int kind;
            int reserved0;
            int reserved1;
        } javan_zone_id_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long epoch_millis;
        } javan_instant_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long epoch_millis;
        } javan_date_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long epoch_millis;
        } javan_sql_date_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long epoch_millis;
        } javan_sql_time_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long epoch_millis;
        } javan_sql_timestamp_value;

        typedef struct {
            int magic;
            int year;
            int month;
            int day;
        } javan_local_date_value;

        typedef struct {
            int magic;
            int hour;
            int minute;
            int second;
            int millis;
        } javan_local_time_value;

        typedef struct {
            int magic;
            int year;
            int month;
            int day;
            int hour;
            int minute;
            int second;
            int millis;
        } javan_local_date_time_value;

        typedef struct {
            int magic;
            int zone_kind;
            int reserved0;
            int reserved1;
            long long epoch_millis;
        } javan_zoned_date_time_value;

        typedef struct {
            int magic;
            int zone_kind;
            int reserved0;
            int reserved1;
            long long epoch_millis;
        } javan_offset_date_time_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            long long epoch_millis;
        } javan_calendar_value;

        typedef struct {
            int magic;
            int value;
            int reserved0;
            int reserved1;
            const char* name;
        } javan_logging_level_value;

        typedef struct {
            int magic;
            int pattern_kind;
            int reserved0;
            int reserved1;
        } javan_simple_date_format_value;

        typedef struct {
            int magic;
            int reserved0;
            int reserved1;
            int reserved2;
            unsigned long long most;
            unsigned long long least;
        } javan_uuid_value;

        typedef struct {
            int magic;
            int case_insensitive;
            int optional_depth;
            int optional_nano_fraction;
            int fraction_min_width;
            int fraction_max_width;
            int fraction_decimal_point;
            int reserved;
            javan_object_list* patterns;
        } javan_datetime_formatter_builder_value;

        typedef struct {
            int magic;
            int case_insensitive;
            int optional_nano_fraction;
            int fraction_min_width;
            int fraction_max_width;
            int fraction_decimal_point;
            int reserved0;
            int reserved1;
            javan_object_list* patterns;
            void* locale;
        } javan_datetime_formatter_value;

        #define JAVAN_OBJECT_LIST_MAGIC 0x4a4c5354
        #define JAVAN_OBJECT_ITERATOR_MAGIC 0x4a495452
        #define JAVAN_OBJECT_MAP_MAGIC 0x4a4d4150
        #define JAVAN_STRING_BUILDER_MAGIC 0x4a53424c
        #define JAVAN_OPTIONAL_MAGIC 0x4a4f5054
        #define JAVAN_OPTIONAL_INT_MAGIC 0x4a4f5049
        #define JAVAN_ATOMIC_BOOLEAN_MAGIC 0x4a415442
        #define JAVAN_ATOMIC_INTEGER_MAGIC 0x4a415449
        #define JAVAN_ATOMIC_LONG_MAGIC 0x4a41544c
        #define JAVAN_ATOMIC_REFERENCE_MAGIC 0x4a415452
        #define JAVAN_THROWABLE_MAGIC 0x4a545242
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
        #define JAVAN_RUNTIME_MAGIC 0x4a52554e
        #define JAVAN_THREAD_MXBEAN_MAGIC 0x4a544d58
        #define JAVAN_RUNTIME_MXBEAN_MAGIC 0x4a524d58
        #define JAVAN_OPERATING_SYSTEM_MXBEAN_MAGIC 0x4a4f4d58
        #define JAVAN_MEMORY_MXBEAN_MAGIC 0x4a4d4d58
        #define JAVAN_MEMORY_USAGE_MAGIC 0x4a4d5553
        #define JAVAN_COUNT_DOWN_LATCH_MAGIC 0x4a43444c
        #define JAVAN_FUTURE_MAGIC 0x4a465554
        #define JAVAN_THREAD_INFO_MAGIC 0x4a54494e
        #define JAVAN_PROCESS_HANDLE_MAGIC 0x4a505248
        #define JAVAN_HTTP_EXCHANGE_MAGIC 0x4a485845
        #define JAVAN_HTTP_INPUT_STREAM_MAGIC 0x4a484953
        #define JAVAN_HTTP_OUTPUT_STREAM_MAGIC 0x4a484f53
        #define JAVAN_HTTP_SERVER_MAGIC 0x4a485356
        #define JAVAN_HTTP_CONTEXT_MAGIC 0x4a485443
        #define JAVAN_LOCALE_MAGIC 0x4a4c4f43
        #define JAVAN_DATETIME_FORMATTER_BUILDER_MAGIC 0x4a445446
        #define JAVAN_DATETIME_FORMATTER_MAGIC 0x4a44544d
        #define JAVAN_ZONE_ID_MAGIC 0x4a5a4f4e
        #define JAVAN_INSTANT_MAGIC 0x4a494e53
        #define JAVAN_DATE_MAGIC 0x4a444154
        #define JAVAN_SQL_DATE_MAGIC 0x4a534441
        #define JAVAN_SQL_TIME_MAGIC 0x4a535449
        #define JAVAN_SQL_TIMESTAMP_MAGIC 0x4a535453
        #define JAVAN_LOCAL_DATE_MAGIC 0x4a4c4441
        #define JAVAN_LOCAL_TIME_MAGIC 0x4a4c544d
        #define JAVAN_LOCAL_DATE_TIME_MAGIC 0x4a4c4454
        #define JAVAN_ZONED_DATE_TIME_MAGIC 0x4a5a4454
        #define JAVAN_OFFSET_DATE_TIME_MAGIC 0x4a4f4454
        #define JAVAN_CALENDAR_MAGIC 0x4a43414c
        #define JAVAN_LOGGING_LEVEL_MAGIC 0x4a4c4f47
        #define JAVAN_SIMPLE_DATE_FORMAT_MAGIC 0x4a534446
        #define JAVAN_UUID_MAGIC 0x4a555549
        #define JAVAN_SIMPLE_DATE_FORMAT_PATTERN_NANO_LOG 1
        #define JAVAN_ZONE_KIND_SYSTEM_DEFAULT 1
        #define JAVAN_ZONE_KIND_FIXED_OFFSET 2
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
        static void* javan_locale_root_value = NULL;
        static void* javan_runtime_root_value = NULL;
        static void* javan_thread_mxbean_root_value = NULL;
        static void* javan_runtime_mxbean_root_value = NULL;
        static void* javan_memory_mxbean_root_value = NULL;
        static void* javan_operating_system_mxbean_root_value = NULL;
        static void* javan_process_handle_root_value = NULL;
        static void* javan_logging_level_off_root_value = NULL;
        static void* javan_logging_level_severe_root_value = NULL;
        static void* javan_logging_level_warning_root_value = NULL;
        static void* javan_logging_level_info_root_value = NULL;
        static void* javan_logging_level_fine_root_value = NULL;
        static void* javan_logging_level_finer_root_value = NULL;
        static void* javan_logging_level_all_root_value = NULL;
        static int javan_management_start_time_initialized_value = 0;
        static long long javan_management_start_time_millis_value = 0;
        static JavanTypeDescriptor* javan_type_descriptor_for(int type_id);
        static int javan_type_descriptor_contains_assignable_name(
            JavanTypeDescriptor* descriptor,
            const char* binary_name
        );
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
        static javan_object_list* javan_list_new_with_capacity(int capacity, int immutable);
        static void javan_list_append_raw(javan_object_list* list, void* value);
        static javan_thread* javan_require_thread(void* value);
        static int javan_thread_current_interrupted_peek(void);
        static javan_locale_value* javan_locale_checked(void* value);
        static javan_runtime_value* javan_runtime_checked(void* value);
        static javan_thread_mxbean_value* javan_thread_mxbean_checked(void* value);
        static javan_count_down_latch_value* javan_count_down_latch_checked(void* value);
        static javan_future_value* javan_future_checked(void* value);
        static javan_thread_info_value* javan_thread_info_checked(void* value);
        static javan_runtime_mxbean_value* javan_runtime_mxbean_checked(void* value);
        static javan_memory_mxbean_value* javan_memory_mxbean_checked(void* value);
        static javan_memory_usage_value* javan_memory_usage_checked(void* value);
        static javan_operating_system_mxbean_value* javan_operating_system_mxbean_checked(void* value);
        static javan_process_handle_value* javan_process_handle_checked(void* value);
        static javan_zone_id_value* javan_zone_id_checked(void* value);
        static javan_zone_id_value* javan_zone_offset_checked(void* value);
        static javan_instant_value* javan_instant_checked(void* value);
        static javan_date_value* javan_date_checked(void* value);
        static javan_sql_date_value* javan_sql_date_checked(void* value);
        static javan_sql_time_value* javan_sql_time_checked(void* value);
        static javan_sql_timestamp_value* javan_sql_timestamp_checked(void* value);
        static javan_local_date_value* javan_local_date_checked(void* value);
        static javan_local_time_value* javan_local_time_checked(void* value);
        static javan_local_date_time_value* javan_local_date_time_checked(void* value);
        static javan_zoned_date_time_value* javan_zoned_date_time_checked(void* value);
        static javan_offset_date_time_value* javan_offset_date_time_checked(void* value);
        static javan_calendar_value* javan_calendar_checked(void* value);
        static javan_logging_level_value* javan_logging_level_checked(void* value);
        static javan_simple_date_format_value* javan_simple_date_format_checked(void* value);
        static javan_uuid_value* javan_uuid_checked(void* value);
        static const char* javan_charsequence_string_value(void* value);
        static int javan_ascii_is_digit(unsigned char value);
        int javan_string_length(const char* value);
        int javan_string_equals(const char* left, const char* right);
        static const char* javan_runtime_kind_binary_name(int runtime_kind);
        static void javan_runtime_run_shutdown_hooks(void);
        void* javan_printable_object_string(void* value);
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
        #define JAVAN_TYPE_JAVA_LANG_CHARACTER -1010
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
                || type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER
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
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_ITERATOR
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP
                || runtime_kind == JAVAN_RUNTIME_KIND_OPTIONAL
                || runtime_kind == JAVAN_RUNTIME_KIND_OPTIONAL_INT
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_LONG
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE
                || runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY
                || runtime_kind == JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR
                || runtime_kind == JAVAN_RUNTIME_KIND_CLASS
                || runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME
                || runtime_kind == JAVAN_RUNTIME_KIND_THREAD_MXBEAN
                || runtime_kind == JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH
                || runtime_kind == JAVAN_RUNTIME_KIND_FUTURE
                || runtime_kind == JAVAN_RUNTIME_KIND_THREAD_INFO
                || runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN
                || runtime_kind == JAVAN_RUNTIME_KIND_MEMORY_MXBEAN
                || runtime_kind == JAVAN_RUNTIME_KIND_MEMORY_USAGE
                || runtime_kind == JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN
                || runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_HANDLE
                || runtime_kind == JAVAN_RUNTIME_KIND_THROWABLE
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
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_RESPONSE
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_EXCHANGE
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_SERVER
                || runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CONTEXT
                || runtime_kind == JAVAN_RUNTIME_KIND_LOCALE
                || runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER
                || runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER
                || runtime_kind == JAVAN_RUNTIME_KIND_ZONE_ID
                || runtime_kind == JAVAN_RUNTIME_KIND_INSTANT
                || runtime_kind == JAVAN_RUNTIME_KIND_DATE
                || runtime_kind == JAVAN_RUNTIME_KIND_SQL_DATE
                || runtime_kind == JAVAN_RUNTIME_KIND_SQL_TIME
                || runtime_kind == JAVAN_RUNTIME_KIND_SQL_TIMESTAMP
                || runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE
                || runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_TIME
                || runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME
                || runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                || runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME
                || runtime_kind == JAVAN_RUNTIME_KIND_CALENDAR
                || runtime_kind == JAVAN_RUNTIME_KIND_LOGGING_LEVEL
                || runtime_kind == JAVAN_RUNTIME_KIND_SIMPLE_DATE_FORMAT;
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
        """;

    private static final String SOURCE_HEAP_VALIDATION = """

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
                if (list->backing != NULL) {
                    if (list->values != NULL || list->length != 0 || list->capacity != 0) {
                        javan_panic("invalid runtime list view metadata");
                    }
                    javan_validate_runtime_managed_reference((void*) list->backing);
                } else {
                    javan_validate_owned_runtime_buffer_reference((void*) list->values);
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET) {
                javan_object_list* list = (javan_object_list*) node->value;
                if (list->magic != JAVAN_OBJECT_LIST_MAGIC || list->length < 0 || list->capacity < 0 || list->length > list->capacity) {
                    javan_panic("invalid runtime set metadata");
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_CLASS) {
                javan_runtime_class_state* state = (javan_runtime_class_state*) node->value;
                if (state->magic != JAVAN_RUNTIME_CLASS_MAGIC || state->binary_name == NULL || state->binary_name[0] == '\\0') {
                    javan_panic("invalid runtime class metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME) {
                javan_runtime_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_THREAD_MXBEAN) {
                javan_thread_mxbean_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH) {
                javan_count_down_latch_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_FUTURE) {
                javan_future_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_THREAD_INFO) {
                javan_thread_info_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN) {
                javan_runtime_mxbean_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_MEMORY_MXBEAN) {
                javan_memory_mxbean_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_MEMORY_USAGE) {
                javan_memory_usage_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN) {
                javan_operating_system_mxbean_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_HANDLE) {
                javan_process_handle_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOGGING_LEVEL) {
                javan_logging_level_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SIMPLE_DATE_FORMAT) {
                javan_simple_date_format_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN) {
                javan_atomic_boolean* state = (javan_atomic_boolean*) node->value;
                if (state->magic != JAVAN_ATOMIC_BOOLEAN_MAGIC || (state->value != 0 && state->value != 1)) {
                    javan_panic("invalid runtime atomic boolean metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER) {
                javan_atomic_integer* state = (javan_atomic_integer*) node->value;
                if (state->magic != JAVAN_ATOMIC_INTEGER_MAGIC) {
                    javan_panic("invalid runtime atomic integer metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_LONG) {
                javan_atomic_long* state = (javan_atomic_long*) node->value;
                if (state->magic != JAVAN_ATOMIC_LONG_MAGIC) {
                    javan_panic("invalid runtime atomic long metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE) {
                javan_atomic_reference* state = (javan_atomic_reference*) node->value;
                if (state->magic != JAVAN_ATOMIC_REFERENCE_MAGIC) {
                    javan_panic("invalid runtime atomic reference metadata");
                }
                javan_validate_runtime_managed_reference(state->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_THROWABLE) {
                javan_throwable_value* throwable = (javan_throwable_value*) node->value;
                if (throwable->magic != JAVAN_THROWABLE_MAGIC || throwable->binary_name == NULL || throwable->binary_name[0] == '\\0') {
                    javan_panic("invalid runtime throwable metadata");
                }
                javan_validate_runtime_managed_reference(throwable->message);
                javan_validate_runtime_managed_reference((void*) throwable->suppressed);
                javan_validate_runtime_managed_reference(throwable->stack_trace);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCALE) {
                javan_locale_value* locale = (javan_locale_value*) node->value;
                if (locale->magic != JAVAN_LOCALE_MAGIC || locale->kind != 1) {
                    javan_panic("invalid runtime locale metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER) {
                javan_datetime_formatter_builder_value* builder = (javan_datetime_formatter_builder_value*) node->value;
                if (builder->magic != JAVAN_DATETIME_FORMATTER_BUILDER_MAGIC
                    || builder->patterns == NULL
                    || builder->optional_depth < 0
                    || (builder->optional_nano_fraction != 0 && builder->optional_nano_fraction != 1)) {
                    javan_panic("invalid runtime datetime formatter builder metadata");
                }
                javan_validate_runtime_managed_reference((void*) builder->patterns);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER) {
                javan_datetime_formatter_value* formatter = (javan_datetime_formatter_value*) node->value;
                if (formatter->magic != JAVAN_DATETIME_FORMATTER_MAGIC
                    || formatter->patterns == NULL
                    || formatter->locale == NULL
                    || (formatter->optional_nano_fraction != 0 && formatter->optional_nano_fraction != 1)) {
                    javan_panic("invalid runtime datetime formatter metadata");
                }
                javan_validate_runtime_managed_reference((void*) formatter->patterns);
                javan_validate_runtime_managed_reference(formatter->locale);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ZONE_ID) {
                javan_zone_id_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_INSTANT) {
                javan_instant_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_DATE) {
                javan_date_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SQL_DATE) {
                javan_sql_date_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SQL_TIME) {
                javan_sql_time_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SQL_TIMESTAMP) {
                javan_sql_timestamp_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE) {
                javan_local_date_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_TIME) {
                javan_local_time_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                javan_local_date_time_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                javan_zoned_date_time_checked(node->value);
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_EXCHANGE) {
                javan_http_exchange_value* exchange = (javan_http_exchange_value*) node->value;
                if (exchange->magic != JAVAN_HTTP_EXCHANGE_MAGIC
                    || exchange->request_uri == NULL
                    || exchange->request_method == NULL
                    || exchange->request_headers == NULL
                    || exchange->request_body == NULL
                    || exchange->response_headers == NULL
                    || exchange->response_body == NULL
                    || exchange->response_content_length < -1) {
                    javan_panic("invalid runtime http exchange metadata");
                }
                javan_validate_runtime_managed_reference((void*) exchange->request_uri);
                javan_validate_runtime_managed_reference((void*) exchange->request_headers);
                javan_validate_runtime_managed_reference(exchange->request_body);
                javan_validate_runtime_managed_reference((void*) exchange->response_headers);
                javan_validate_runtime_managed_reference(exchange->response_body);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM) {
                javan_http_input_stream_value* stream = (javan_http_input_stream_value*) node->value;
                if (stream->magic != JAVAN_HTTP_INPUT_STREAM_MAGIC
                    || stream->offset < 0
                    || stream->closed < 0
                    || stream->bytes == NULL) {
                    javan_panic("invalid runtime http input stream metadata");
                }
                javan_validate_runtime_managed_reference(stream->bytes);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM) {
                javan_http_output_stream_value* stream = (javan_http_output_stream_value*) node->value;
                if (stream->magic != JAVAN_HTTP_OUTPUT_STREAM_MAGIC
                    || stream->closed < 0
                    || stream->exchange == NULL) {
                    javan_panic("invalid runtime http output stream metadata");
                }
                javan_validate_runtime_managed_reference((void*) stream->exchange);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_SERVER) {
                javan_http_server_value* server = (javan_http_server_value*) node->value;
                if (server->magic != JAVAN_HTTP_SERVER_MAGIC
                    || server->address == NULL
                    || server->server_socket == NULL
                    || server->contexts == NULL
                    || server->started < 0
                    || server->stop_requested < 0
                    || server->completed < 0
                    || server->backlog < 0) {
                    javan_panic("invalid runtime http server metadata");
                }
                javan_validate_runtime_managed_reference((void*) server->address);
                javan_validate_runtime_managed_reference((void*) server->server_socket);
                javan_validate_runtime_managed_reference((void*) server->contexts);
                javan_validate_runtime_managed_reference(server->executor);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CONTEXT) {
                javan_http_context_value* context = (javan_http_context_value*) node->value;
                if (context->magic != JAVAN_HTTP_CONTEXT_MAGIC
                    || context->server == NULL
                    || context->path == NULL
                    || context->handler == NULL) {
                    javan_panic("invalid runtime http context metadata");
                }
                javan_validate_runtime_managed_reference((void*) context->server);
                javan_validate_runtime_managed_reference((void*) context->path);
                javan_validate_runtime_managed_reference(context->handler);
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_EXCHANGE) {
                javan_http_exchange_value* exchange = (javan_http_exchange_value*) node->value;
                if (exchange->magic != JAVAN_HTTP_EXCHANGE_MAGIC
                    || exchange->request_uri == NULL
                    || exchange->request_method == NULL
                    || exchange->request_headers == NULL
                    || exchange->request_body == NULL
                    || exchange->response_headers == NULL
                    || exchange->response_body == NULL
                    || exchange->response_content_length < -1) {
                    javan_panic("invalid runtime http exchange metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM) {
                javan_http_input_stream_value* stream = (javan_http_input_stream_value*) node->value;
                if (stream->magic != JAVAN_HTTP_INPUT_STREAM_MAGIC
                    || stream->offset < 0
                    || stream->closed < 0
                    || stream->bytes == NULL) {
                    javan_panic("invalid runtime http input stream metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM) {
                javan_http_output_stream_value* stream = (javan_http_output_stream_value*) node->value;
                if (stream->magic != JAVAN_HTTP_OUTPUT_STREAM_MAGIC
                    || stream->closed < 0
                    || stream->exchange == NULL) {
                    javan_panic("invalid runtime http output stream metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_SERVER) {
                javan_http_server_value* server = (javan_http_server_value*) node->value;
                if (server->magic != JAVAN_HTTP_SERVER_MAGIC
                    || server->address == NULL
                    || server->server_socket == NULL
                    || server->contexts == NULL
                    || server->started < 0
                    || server->stop_requested < 0
                    || server->completed < 0
                    || server->backlog < 0) {
                    javan_panic("invalid runtime http server metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CONTEXT) {
                javan_http_context_value* context = (javan_http_context_value*) node->value;
                if (context->magic != JAVAN_HTTP_CONTEXT_MAGIC
                    || context->server == NULL
                    || context->path == NULL
                    || context->handler == NULL) {
                    javan_panic("invalid runtime http context metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCALE) {
                javan_locale_value* locale = (javan_locale_value*) node->value;
                if (locale->magic != JAVAN_LOCALE_MAGIC || locale->kind != 1) {
                    javan_panic("invalid runtime locale metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER) {
                javan_datetime_formatter_builder_value* builder = (javan_datetime_formatter_builder_value*) node->value;
                if (builder->magic != JAVAN_DATETIME_FORMATTER_BUILDER_MAGIC
                    || builder->patterns == NULL
                    || builder->optional_depth < 0
                    || (builder->optional_nano_fraction != 0 && builder->optional_nano_fraction != 1)) {
                    javan_panic("invalid runtime datetime formatter builder metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER) {
                javan_datetime_formatter_value* formatter = (javan_datetime_formatter_value*) node->value;
                if (formatter->magic != JAVAN_DATETIME_FORMATTER_MAGIC
                    || formatter->patterns == NULL
                    || formatter->locale == NULL
                    || (formatter->optional_nano_fraction != 0 && formatter->optional_nano_fraction != 1)) {
                    javan_panic("invalid runtime datetime formatter metadata");
                }
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ZONE_ID) {
                javan_zone_id_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_INSTANT) {
                javan_instant_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_DATE) {
                javan_date_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SQL_DATE) {
                javan_sql_date_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SQL_TIME) {
                javan_sql_time_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_SQL_TIMESTAMP) {
                javan_sql_timestamp_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE) {
                javan_local_date_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_TIME) {
                javan_local_time_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                javan_local_date_time_checked(node->value);
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                javan_zoned_date_time_checked(node->value);
            }
        }

        static void* javan_optional_int_to_string(void* value) {
            javan_optional_int* optional = (javan_optional_int*) value;
            if (optional == NULL || optional->magic != JAVAN_OPTIONAL_INT_MAGIC) {
                javan_panic("invalid optional int printable object");
            }
            if (optional->present == 0) {
                return (void*) "OptionalInt.empty";
            }
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "OptionalInt[%d]", optional->value);
            return javan_string_copy(buffer);
        }

        void* javan_printable_object_string(void* value) {
            if (value == NULL) {
                return (void*) "null";
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
            int type_id = node->type_id;
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return javan_string_value_of_int(javan_integer_int_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return javan_string_value_of_long(javan_long_long_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_string_value_of_float(javan_float_float_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_string_value_of_double(javan_double_double_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN) {
                return javan_string_value_of_bool(javan_boolean_boolean_value(value));
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                return javan_string_value_of_char(javan_character_char_value(value));
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
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME
                || node->runtime_kind == JAVAN_RUNTIME_KIND_THREAD_MXBEAN
                || node->runtime_kind == JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH
                || node->runtime_kind == JAVAN_RUNTIME_KIND_FUTURE
                || node->runtime_kind == JAVAN_RUNTIME_KIND_THREAD_INFO
                || node->runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN
                || node->runtime_kind == JAVAN_RUNTIME_KIND_MEMORY_MXBEAN
                || node->runtime_kind == JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN
                || node->runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_HANDLE) {
                return javan_string_from(javan_runtime_kind_binary_name(node->runtime_kind));
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_THROWABLE) {
                return javan_throwable_get_message(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_OPTIONAL_INT) {
                return javan_optional_int_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_LOGGING_LEVEL) {
                return javan_logging_level_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_UUID) {
                return javan_uuid_to_string(value);
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS) {
                return javan_inet_socket_address_to_string(value);
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
                if (node->runtime_kind != JAVAN_RUNTIME_KIND_NONE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_LIST
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_SET
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_ITERATOR
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OBJECT_MAP
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OPTIONAL
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OPTIONAL_INT
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
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_EXCHANGE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_SERVER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_HTTP_CONTEXT
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_LOCALE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_DATETIME_FORMATTER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ZONE_ID
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_INSTANT
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_DATE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SQL_DATE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SQL_TIME
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SQL_TIMESTAMP
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_LOCAL_DATE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_LOCAL_TIME
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_CLASS
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_RUNTIME
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_THREAD_MXBEAN
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_FUTURE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_THREAD_INFO
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_MEMORY_MXBEAN
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_MEMORY_USAGE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_PROCESS_HANDLE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_LOGGING_LEVEL
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_SIMPLE_DATE_FORMAT
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_INTEGER
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_LONG
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE
                    && node->runtime_kind != JAVAN_RUNTIME_KIND_THROWABLE) {
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET) {
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
            } else if (node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_SERVER) {
                javan_http_server_value* server = (javan_http_server_value*) node->value;
                if (server != NULL && server->magic == JAVAN_HTTP_SERVER_MAGIC && server->server_socket != NULL && server->server_socket->fd >= 0) {
                    javan_socket_native_close(server->server_socket->fd);
                    server->server_socket->fd = -1;
                    server->server_socket->closed = 1;
                }
            }
        }

        static void javan_release_thread_native_state(javan_thread* thread);
        static void javan_http_server_wait_for_completion(javan_http_server_value* server);
        static javan_object_map* javan_map_checked(void* value);
        static void javan_map_ensure_capacity(javan_object_map* map, int required);
        void* javan_hashmap_new(void);
        void* javan_map_remove(void* value, void* key);
        static javan_thread* javan_current_thread_object(void);
        static javan_thread* javan_require_thread(void* value);
        static int javan_thread_has_live_lifecycle(javan_thread* thread);

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

        static int javan_runtime_kind_of(void* value) {
            if (value == NULL) {
                return JAVAN_RUNTIME_KIND_NONE;
            }
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            int result = node == NULL ? JAVAN_RUNTIME_KIND_NONE : node->runtime_kind;
            javan_runtime_lock_leave();
            return result;
        }

        int javan_object_is_number(void* value) {
            if (value == NULL) {
                return 0;
            }
            int type_id = javan_registered_type_id(value);
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER
                || type_id == JAVAN_TYPE_JAVA_LANG_LONG
                || type_id == JAVAN_TYPE_JAVA_LANG_FLOAT
                || type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return 1;
            }
            int runtime_kind = javan_runtime_kind_of(value);
            return runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER
                || runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_LONG;
        }

        int javan_object_is_atomic_boolean(void* value) {
            return javan_runtime_kind_of(value) == JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN;
        }

        int javan_object_is_atomic_integer(void* value) {
            return javan_runtime_kind_of(value) == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER;
        }

        int javan_object_is_atomic_long(void* value) {
            return javan_runtime_kind_of(value) == JAVAN_RUNTIME_KIND_ATOMIC_LONG;
        }

        int javan_object_is_atomic_reference(void* value) {
            return javan_runtime_kind_of(value) == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE;
        }

        int javan_object_is_collection(void* value) {
            int runtime_kind = javan_runtime_kind_of(value);
            return runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET;
        }

        int javan_object_is_optional(void* value) {
            return javan_runtime_kind_of(value) == JAVAN_RUNTIME_KIND_OPTIONAL;
        }

        int javan_object_is_string(void* value) {
            if (value == NULL) {
                return 0;
            }
            if (javan_runtime_kind_of(value) == JAVAN_RUNTIME_KIND_STRING) {
                return 1;
            }
            return javan_registered_type_id(value) == 0 ? 1 : 0;
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

        static javan_runtime_value* javan_runtime_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported runtime");
            }
            javan_runtime_value* state = (javan_runtime_value*) value;
            if (state->magic != JAVAN_RUNTIME_MAGIC) {
                javan_panic("unsupported runtime");
            }
            return state;
        }

        static javan_thread_mxbean_value* javan_thread_mxbean_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported thread mxbean");
            }
            javan_thread_mxbean_value* state = (javan_thread_mxbean_value*) value;
            if (state->magic != JAVAN_THREAD_MXBEAN_MAGIC) {
                javan_panic("unsupported thread mxbean");
            }
            return state;
        }

        static javan_count_down_latch_value* javan_count_down_latch_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported CountDownLatch");
            }
            javan_count_down_latch_value* state = (javan_count_down_latch_value*) value;
            if (state->magic != JAVAN_COUNT_DOWN_LATCH_MAGIC || state->count < 0) {
                javan_panic("unsupported CountDownLatch");
            }
            return state;
        }

        static javan_future_value* javan_future_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported Future");
            }
            javan_future_value* state = (javan_future_value*) value;
            if (state->magic != JAVAN_FUTURE_MAGIC || (state->cancelled != 0 && state->cancelled != 1)) {
                javan_panic("unsupported Future");
            }
            return state;
        }

        static javan_thread_info_value* javan_thread_info_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported ThreadInfo");
            }
            javan_thread_info_value* state = (javan_thread_info_value*) value;
            if (state->magic != JAVAN_THREAD_INFO_MAGIC || state->thread_id < 0LL) {
                javan_panic("unsupported ThreadInfo");
            }
            return state;
        }

        static javan_runtime_mxbean_value* javan_runtime_mxbean_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported runtime mxbean");
            }
            javan_runtime_mxbean_value* state = (javan_runtime_mxbean_value*) value;
            if (state->magic != JAVAN_RUNTIME_MXBEAN_MAGIC) {
                javan_panic("unsupported runtime mxbean");
            }
            return state;
        }

        static javan_memory_mxbean_value* javan_memory_mxbean_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported memory mxbean");
            }
            javan_memory_mxbean_value* state = (javan_memory_mxbean_value*) value;
            if (state->magic != JAVAN_MEMORY_MXBEAN_MAGIC) {
                javan_panic("unsupported memory mxbean");
            }
            return state;
        }

        static javan_memory_usage_value* javan_memory_usage_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported memory usage");
            }
            javan_memory_usage_value* state = (javan_memory_usage_value*) value;
            if (state->magic != JAVAN_MEMORY_USAGE_MAGIC
                || state->used < 0
                || state->committed < state->used
                || state->max < state->committed) {
                javan_panic("unsupported memory usage");
            }
            return state;
        }

        static javan_operating_system_mxbean_value* javan_operating_system_mxbean_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported operating system mxbean");
            }
            javan_operating_system_mxbean_value* state = (javan_operating_system_mxbean_value*) value;
            if (state->magic != JAVAN_OPERATING_SYSTEM_MXBEAN_MAGIC) {
                javan_panic("unsupported operating system mxbean");
            }
            return state;
        }

        static javan_process_handle_value* javan_process_handle_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported process handle");
            }
            javan_process_handle_value* state = (javan_process_handle_value*) value;
            if (state->magic != JAVAN_PROCESS_HANDLE_MAGIC || state->pid <= 0) {
                javan_panic("unsupported process handle");
            }
            return state;
        }

        static javan_locale_value* javan_locale_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported locale");
            }
            javan_locale_value* locale = (javan_locale_value*) value;
            if (locale->magic != JAVAN_LOCALE_MAGIC || locale->kind != 1) {
                javan_panic("unsupported locale");
            }
            return locale;
        }

        static javan_zone_id_value* javan_zone_id_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported zone id");
            }
            javan_zone_id_value* zone = (javan_zone_id_value*) value;
            if (zone->magic != JAVAN_ZONE_ID_MAGIC || zone->kind != JAVAN_ZONE_KIND_SYSTEM_DEFAULT) {
                javan_panic("unsupported zone id");
            }
            return zone;
        }

        static javan_zone_id_value* javan_zone_offset_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported zone offset");
            }
            javan_zone_id_value* zone = (javan_zone_id_value*) value;
            if (zone->magic != JAVAN_ZONE_ID_MAGIC || zone->kind != JAVAN_ZONE_KIND_FIXED_OFFSET) {
                javan_panic("unsupported zone offset");
            }
            return zone;
        }

        static javan_instant_value* javan_instant_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported instant");
            }
            javan_instant_value* instant = (javan_instant_value*) value;
            if (instant->magic != JAVAN_INSTANT_MAGIC) {
                javan_panic("unsupported instant");
            }
            return instant;
        }

        static javan_date_value* javan_date_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported date");
            }
            javan_date_value* date = (javan_date_value*) value;
            if (date->magic != JAVAN_DATE_MAGIC) {
                javan_panic("unsupported date");
            }
            return date;
        }

        static javan_sql_date_value* javan_sql_date_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported sql date");
            }
            javan_sql_date_value* date = (javan_sql_date_value*) value;
            if (date->magic != JAVAN_SQL_DATE_MAGIC) {
                javan_panic("unsupported sql date");
            }
            return date;
        }

        static javan_sql_time_value* javan_sql_time_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported sql time");
            }
            javan_sql_time_value* time = (javan_sql_time_value*) value;
            if (time->magic != JAVAN_SQL_TIME_MAGIC) {
                javan_panic("unsupported sql time");
            }
            return time;
        }

        static javan_sql_timestamp_value* javan_sql_timestamp_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported sql timestamp");
            }
            javan_sql_timestamp_value* timestamp = (javan_sql_timestamp_value*) value;
            if (timestamp->magic != JAVAN_SQL_TIMESTAMP_MAGIC) {
                javan_panic("unsupported sql timestamp");
            }
            return timestamp;
        }

        static javan_local_date_value* javan_local_date_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported local date");
            }
            javan_local_date_value* date = (javan_local_date_value*) value;
            if (date->magic != JAVAN_LOCAL_DATE_MAGIC) {
                javan_panic("unsupported local date");
            }
            return date;
        }

        static javan_local_time_value* javan_local_time_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported local time");
            }
            javan_local_time_value* time = (javan_local_time_value*) value;
            if (time->magic != JAVAN_LOCAL_TIME_MAGIC) {
                javan_panic("unsupported local time");
            }
            return time;
        }

        static javan_local_date_time_value* javan_local_date_time_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported local date time");
            }
            javan_local_date_time_value* date_time = (javan_local_date_time_value*) value;
            if (date_time->magic != JAVAN_LOCAL_DATE_TIME_MAGIC) {
                javan_panic("unsupported local date time");
            }
            return date_time;
        }

        static javan_zoned_date_time_value* javan_zoned_date_time_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported zoned date time");
            }
            javan_zoned_date_time_value* date_time = (javan_zoned_date_time_value*) value;
            if (date_time->magic != JAVAN_ZONED_DATE_TIME_MAGIC || date_time->zone_kind != JAVAN_ZONE_KIND_SYSTEM_DEFAULT) {
                javan_panic("unsupported zoned date time");
            }
            return date_time;
        }

        static javan_offset_date_time_value* javan_offset_date_time_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported offset date time");
            }
            javan_offset_date_time_value* date_time = (javan_offset_date_time_value*) value;
            if (date_time->magic != JAVAN_OFFSET_DATE_TIME_MAGIC || date_time->zone_kind != JAVAN_ZONE_KIND_SYSTEM_DEFAULT) {
                javan_panic("unsupported offset date time");
            }
            return date_time;
        }

        static javan_calendar_value* javan_calendar_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported calendar");
            }
            javan_calendar_value* calendar = (javan_calendar_value*) value;
            if (calendar->magic != JAVAN_CALENDAR_MAGIC) {
                javan_panic("unsupported calendar");
            }
            return calendar;
        }

        static javan_logging_level_value* javan_logging_level_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported logging level");
            }
            javan_logging_level_value* level = (javan_logging_level_value*) value;
            if (level->magic != JAVAN_LOGGING_LEVEL_MAGIC || level->name == NULL || level->name[0] == '\\0') {
                javan_panic("unsupported logging level");
            }
            return level;
        }

        static javan_simple_date_format_value* javan_simple_date_format_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported simple date format");
            }
            javan_simple_date_format_value* formatter = (javan_simple_date_format_value*) value;
            if (formatter->magic != JAVAN_SIMPLE_DATE_FORMAT_MAGIC
                || formatter->pattern_kind != JAVAN_SIMPLE_DATE_FORMAT_PATTERN_NANO_LOG) {
                javan_panic("unsupported simple date format");
            }
            return formatter;
        }

        static javan_uuid_value* javan_uuid_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported uuid");
            }
            javan_uuid_value* uuid = (javan_uuid_value*) value;
            if (uuid->magic != JAVAN_UUID_MAGIC) {
                javan_panic("unsupported uuid");
            }
            return uuid;
        }

        static javan_datetime_formatter_builder_value* javan_datetime_formatter_builder_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported datetime formatter builder");
            }
            javan_datetime_formatter_builder_value* builder = (javan_datetime_formatter_builder_value*) value;
            if (builder->magic != JAVAN_DATETIME_FORMATTER_BUILDER_MAGIC || builder->patterns == NULL || builder->optional_depth < 0) {
                javan_panic("unsupported datetime formatter builder");
            }
            return builder;
        }

        static javan_datetime_formatter_value* javan_datetime_formatter_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported datetime formatter");
            }
            javan_datetime_formatter_value* formatter = (javan_datetime_formatter_value*) value;
            if (formatter->magic != JAVAN_DATETIME_FORMATTER_MAGIC || formatter->patterns == NULL || formatter->locale == NULL) {
                javan_panic("unsupported datetime formatter");
            }
            return formatter;
        }

        static int javan_runtime_class_name_is_array(const char* binary_name) {
            return binary_name != NULL && binary_name[0] == '[';
        }

        static const char* javan_runtime_class_simple_name_source(const char* binary_name) {
            if (binary_name == NULL || binary_name[0] == '\\0') {
                return "";
            }
            const char* simple = binary_name;
            for (const char* cursor = binary_name; *cursor != '\\0'; cursor++) {
                if (*cursor == '.' || *cursor == '$') {
                    simple = cursor + 1;
                }
            }
            return simple;
        }

        static const char* javan_runtime_array_binary_name(int kind) {
            switch (kind) {
                case JAVAN_ARRAY_KIND_OBJECT:
                    return "[Ljava.lang.Object;";
                case JAVAN_ARRAY_KIND_INT:
                    return "[I";
                case JAVAN_ARRAY_KIND_LONG:
                    return "[J";
                case JAVAN_ARRAY_KIND_FLOAT:
                    return "[F";
                case JAVAN_ARRAY_KIND_DOUBLE:
                    return "[D";
                case JAVAN_ARRAY_KIND_BYTE:
                    return "[B";
                case JAVAN_ARRAY_KIND_BOOLEAN:
                    return "[Z";
                case JAVAN_ARRAY_KIND_SHORT:
                    return "[S";
                case JAVAN_ARRAY_KIND_CHAR:
                    return "[C";
                default:
                    return NULL;
            }
        }
        """;

    private static final String SOURCE_HEAP_ALLOC_HEAD_CONT = """
        static const char* javan_runtime_kind_binary_name(int runtime_kind) {
            switch (runtime_kind) {
                case JAVAN_RUNTIME_KIND_OBJECT_LIST:
                    return "java.util.ArrayList";
                case JAVAN_RUNTIME_KIND_OBJECT_SET:
                    return "java.util.HashSet";
                case JAVAN_RUNTIME_KIND_OBJECT_ITERATOR:
                    return "java.util.Iterator";
                case JAVAN_RUNTIME_KIND_OBJECT_MAP:
                    return "java.util.LinkedHashMap";
                case JAVAN_RUNTIME_KIND_OPTIONAL:
                    return "java.util.Optional";
                case JAVAN_RUNTIME_KIND_OPTIONAL_INT:
                    return "java.util.OptionalInt";
                case JAVAN_RUNTIME_KIND_STRING:
                    return "java.lang.String";
                case JAVAN_RUNTIME_KIND_PROCESS_RESULT:
                    return "javan.ProcessResult";
                case JAVAN_RUNTIME_KIND_STRING_BUILDER:
                    return "java.lang.StringBuilder";
                case JAVAN_RUNTIME_KIND_OWNED_BUFFER:
                    return "javan.OwnedBuffer";
                case JAVAN_RUNTIME_KIND_INET_ADDRESS:
                    return "java.net.InetAddress";
                case JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS:
                    return "java.net.InetSocketAddress";
                case JAVAN_RUNTIME_KIND_SOCKET:
                    return "java.net.Socket";
                case JAVAN_RUNTIME_KIND_SERVER_SOCKET:
                    return "java.net.ServerSocket";
                case JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM:
                    return "java.io.InputStream";
                case JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM:
                    return "java.io.OutputStream";
                case JAVAN_RUNTIME_KIND_URI:
                    return "java.net.URI";
                case JAVAN_RUNTIME_KIND_HTTP_CLIENT:
                    return "java.net.http.HttpClient";
                case JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER:
                    return "java.net.http.HttpRequest$Builder";
                case JAVAN_RUNTIME_KIND_HTTP_REQUEST:
                    return "java.net.http.HttpRequest";
                case JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER:
                    return "java.net.http.HttpResponse$BodyHandler";
                case JAVAN_RUNTIME_KIND_HTTP_RESPONSE:
                    return "java.net.http.HttpResponse";
                case JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER:
                    return "java.net.http.HttpRequest$BodyPublisher";
                case JAVAN_RUNTIME_KIND_HTTP_EXCHANGE:
                    return "com.sun.net.httpserver.HttpExchange";
                case JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM:
                    return "java.io.InputStream";
                case JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM:
                    return "java.io.OutputStream";
                case JAVAN_RUNTIME_KIND_HTTP_SERVER:
                    return "com.sun.net.httpserver.HttpServer";
                case JAVAN_RUNTIME_KIND_HTTP_CONTEXT:
                    return "com.sun.net.httpserver.HttpContext";
                case JAVAN_RUNTIME_KIND_LOCALE:
                    return "java.util.Locale";
                case JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER:
                    return "java.time.format.DateTimeFormatterBuilder";
                case JAVAN_RUNTIME_KIND_DATETIME_FORMATTER:
                    return "java.time.format.DateTimeFormatter";
                case JAVAN_RUNTIME_KIND_ZONE_ID:
                    return "java.time.ZoneId";
                case JAVAN_RUNTIME_KIND_INSTANT:
                    return "java.time.Instant";
                case JAVAN_RUNTIME_KIND_DATE:
                    return "java.util.Date";
                case JAVAN_RUNTIME_KIND_SQL_DATE:
                    return "java.sql.Date";
                case JAVAN_RUNTIME_KIND_SQL_TIME:
                    return "java.sql.Time";
                case JAVAN_RUNTIME_KIND_SQL_TIMESTAMP:
                    return "java.sql.Timestamp";
                case JAVAN_RUNTIME_KIND_LOCAL_DATE:
                    return "java.time.LocalDate";
                case JAVAN_RUNTIME_KIND_LOCAL_TIME:
                    return "java.time.LocalTime";
                case JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME:
                    return "java.time.LocalDateTime";
                case JAVAN_RUNTIME_KIND_ZONED_DATE_TIME:
                    return "java.time.ZonedDateTime";
                case JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME:
                    return "java.time.OffsetDateTime";
                case JAVAN_RUNTIME_KIND_CALENDAR:
                    return "java.util.Calendar";
                case JAVAN_RUNTIME_KIND_LOGGING_LEVEL:
                    return "java.util.logging.Level";
                case JAVAN_RUNTIME_KIND_SIMPLE_DATE_FORMAT:
                    return "java.text.SimpleDateFormat";
                case JAVAN_RUNTIME_KIND_UUID:
                    return "java.util.UUID";
                case JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_BUILDER:
                    return "java.lang.ThreadBuilders$VirtualThreadBuilder";
                case JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_FACTORY:
                    return "java.lang.ThreadBuilders$VirtualThreadFactory";
                case JAVAN_RUNTIME_KIND_VIRTUAL_THREAD_EXECUTOR:
                    return "java.util.concurrent.ThreadPerTaskExecutor";
                case JAVAN_RUNTIME_KIND_CLASS:
                    return "java.lang.Class";
                case JAVAN_RUNTIME_KIND_RUNTIME:
                    return "java.lang.Runtime";
                case JAVAN_RUNTIME_KIND_THREAD_MXBEAN:
                    return "java.lang.management.ThreadMXBean";
                case JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH:
                    return "java.util.concurrent.CountDownLatch";
                case JAVAN_RUNTIME_KIND_FUTURE:
                    return "java.util.concurrent.FutureTask";
                case JAVAN_RUNTIME_KIND_THREAD_INFO:
                    return "java.lang.management.ThreadInfo";
                case JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN:
                    return "java.lang.management.RuntimeMXBean";
                case JAVAN_RUNTIME_KIND_MEMORY_MXBEAN:
                    return "java.lang.management.MemoryMXBean";
                case JAVAN_RUNTIME_KIND_MEMORY_USAGE:
                    return "java.lang.management.MemoryUsage";
                case JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN:
                    return "com.sun.management.OperatingSystemMXBean";
                case JAVAN_RUNTIME_KIND_PROCESS_HANDLE:
                    return "java.lang.ProcessHandle";
                case JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN:
                    return "java.util.concurrent.atomic.AtomicBoolean";
                case JAVAN_RUNTIME_KIND_ATOMIC_INTEGER:
                    return "java.util.concurrent.atomic.AtomicInteger";
                case JAVAN_RUNTIME_KIND_ATOMIC_LONG:
                    return "java.util.concurrent.atomic.AtomicLong";
                case JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE:
                    return "java.util.concurrent.atomic.AtomicReference";
                case JAVAN_RUNTIME_KIND_THROWABLE:
                    return "java.lang.Throwable";
                default:
                    return NULL;
            }
        }

        static int javan_runtime_builtin_type_id(const char* binary_name) {
            if (binary_name == NULL) {
                return 0;
            }
            if (strcmp(binary_name, "java.lang.Integer") == 0) {
                return JAVAN_TYPE_JAVA_LANG_INTEGER;
            }
            if (strcmp(binary_name, "java.lang.Long") == 0) {
                return JAVAN_TYPE_JAVA_LANG_LONG;
            }
            if (strcmp(binary_name, "java.lang.Float") == 0) {
                return JAVAN_TYPE_JAVA_LANG_FLOAT;
            }
            if (strcmp(binary_name, "java.lang.Double") == 0) {
                return JAVAN_TYPE_JAVA_LANG_DOUBLE;
            }
            if (strcmp(binary_name, "java.lang.Boolean") == 0) {
                return JAVAN_TYPE_JAVA_LANG_BOOLEAN;
            }
            if (strcmp(binary_name, "java.lang.Character") == 0) {
                return JAVAN_TYPE_JAVA_LANG_CHARACTER;
            }
            if (strcmp(binary_name, "java.lang.Thread") == 0) {
                return JAVAN_TYPE_JAVA_LANG_THREAD;
            }
            if (strcmp(binary_name, "java.nio.file.attribute.FileTime") == 0) {
                return JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME;
            }
            return 0;
        }

        static int javan_value_is_string(void* value) {
            if (value == NULL) {
                return 0;
            }
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            int result = node == NULL || (node->kind == JAVAN_HEAP_KIND_RUNTIME && node->runtime_kind == JAVAN_RUNTIME_KIND_STRING);
            javan_runtime_lock_leave();
            return result;
        }

        static int javan_runtime_class_matches_value(javan_runtime_class_state* state, void* value) {
            if (state == NULL || value == NULL) {
                return 0;
            }
            const char* binary_name = state->binary_name;
            if (strcmp(binary_name, "java.lang.Object") == 0) {
                return 1;
            }
            int type_id = javan_registered_type_id(value);
            JavanTypeDescriptor* descriptor = NULL;
            if (type_id != 0) {
                descriptor = javan_type_descriptor_for(type_id);
                if (descriptor != NULL && javan_type_descriptor_contains_assignable_name(descriptor, binary_name) != 0) {
                    return 1;
                }
            }
            if (strcmp(binary_name, "java.lang.String") == 0) {
                return javan_value_is_string(value);
            }
            if (strcmp(binary_name, "java.lang.Number") == 0) {
                return javan_object_is_number(value);
            }
            if (strcmp(binary_name, "java.lang.Enum") == 0) {
                return descriptor != NULL && descriptor->is_enum != 0;
            }
            int runtime_kind = javan_runtime_kind_of(value);
            if (strcmp(binary_name, "java.util.concurrent.atomic.AtomicBoolean") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN;
            }
            if (strcmp(binary_name, "java.util.concurrent.atomic.AtomicInteger") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_INTEGER;
            }
            if (strcmp(binary_name, "java.util.concurrent.atomic.AtomicLong") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_LONG;
            }
            if (strcmp(binary_name, "java.util.concurrent.atomic.AtomicReference") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE;
            }
            if (strcmp(binary_name, "java.lang.CharSequence") == 0) {
                return javan_value_is_string(value) != 0 || runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER;
            }
            if (strcmp(binary_name, "java.util.List") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST;
            }
            if (strcmp(binary_name, "java.util.Set") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET;
            }
            if (strcmp(binary_name, "java.util.Collection") == 0 || strcmp(binary_name, "java.lang.Iterable") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET;
            }
            if (strcmp(binary_name, "java.util.Map") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_MAP;
            }
            if (state->is_enum != 0) {
                javan_panic("enum instance runtime typing is not supported");
            }
            return 0;
        }

        static int javan_runtime_class_matches_class(javan_runtime_class_state* target, javan_runtime_class_state* source) {
            if (target == NULL || source == NULL) {
                return 0;
            }
            if (strcmp(target->binary_name, source->binary_name) == 0) {
                return 1;
            }
            if (strcmp(target->binary_name, "java.lang.Object") == 0) {
                return 1;
            }
            if (source->type_id != 0) {
                JavanTypeDescriptor* source_descriptor = javan_type_descriptor_for(source->type_id);
                if (source_descriptor != NULL
                    && javan_type_descriptor_contains_assignable_name(source_descriptor, target->binary_name) != 0) {
                    return 1;
                }
            }
            if (strcmp(target->binary_name, "java.lang.Number") == 0) {
                return source->type_id == JAVAN_TYPE_JAVA_LANG_INTEGER
                    || source->type_id == JAVAN_TYPE_JAVA_LANG_LONG
                    || source->type_id == JAVAN_TYPE_JAVA_LANG_FLOAT
                    || source->type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE
                    || strcmp(source->binary_name, "java.util.concurrent.atomic.AtomicInteger") == 0
                    || strcmp(source->binary_name, "java.util.concurrent.atomic.AtomicLong") == 0;
            }
            if (strcmp(target->binary_name, "java.lang.Enum") == 0) {
                return source->is_enum != 0;
            }
            if (strcmp(target->binary_name, "java.lang.CharSequence") == 0) {
                return strcmp(source->binary_name, "java.lang.String") == 0
                    || strcmp(source->binary_name, "java.lang.StringBuilder") == 0;
            }
            if (strcmp(target->binary_name, "java.util.List") == 0) {
                return strcmp(source->binary_name, "java.util.ArrayList") == 0;
            }
            if (strcmp(target->binary_name, "java.util.Set") == 0) {
                return strcmp(source->binary_name, "java.util.HashSet") == 0;
            }
            if (strcmp(target->binary_name, "java.util.Collection") == 0 || strcmp(target->binary_name, "java.lang.Iterable") == 0) {
                return strcmp(source->binary_name, "java.util.ArrayList") == 0
                    || strcmp(source->binary_name, "java.util.HashSet") == 0;
            }
            if (strcmp(target->binary_name, "java.util.Map") == 0) {
                return strcmp(source->binary_name, "java.util.LinkedHashMap") == 0;
            }
            return 0;
        }

        static javan_atomic_boolean* javan_atomic_boolean_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic boolean");
            }
            javan_atomic_boolean* state = (javan_atomic_boolean*) value;
            if (state->magic != JAVAN_ATOMIC_BOOLEAN_MAGIC || (state->value != 0 && state->value != 1)) {
                javan_panic("unsupported atomic boolean");
            }
            return state;
        }

        static javan_atomic_integer* javan_atomic_integer_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic integer");
            }
            javan_atomic_integer* state = (javan_atomic_integer*) value;
            if (state->magic != JAVAN_ATOMIC_INTEGER_MAGIC) {
                javan_panic("unsupported atomic integer");
            }
            return state;
        }

        static javan_atomic_long* javan_atomic_long_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic long");
            }
            javan_atomic_long* state = (javan_atomic_long*) value;
            if (state->magic != JAVAN_ATOMIC_LONG_MAGIC) {
                javan_panic("unsupported atomic long");
            }
            return state;
        }

        static javan_atomic_reference* javan_atomic_reference_checked(void* value) {
            if (value == NULL) {
                javan_panic("unsupported atomic reference");
            }
            javan_atomic_reference* state = (javan_atomic_reference*) value;
            if (state->magic != JAVAN_ATOMIC_REFERENCE_MAGIC) {
                javan_panic("unsupported atomic reference");
            }
            return state;
        }

        static javan_throwable_value* javan_throwable_checked(void* value) {
            if (value == NULL) {
                javan_panic("null Throwable");
            }
            javan_throwable_value* throwable = (javan_throwable_value*) value;
            if (throwable->magic != JAVAN_THROWABLE_MAGIC || throwable->binary_name == NULL || throwable->binary_name[0] == '\\0') {
                javan_panic("unsupported Throwable");
            }
            return throwable;
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
            int type_id = javan_runtime_builtin_type_id(binary_name);
            int is_enum = 0;
            if (type_id == 0) {
                for (int index = 0; index < javan_type_descriptor_count_value; index++) {
                    JavanTypeDescriptor descriptor = javan_type_descriptors_value[index];
                    if (descriptor.name != NULL && strcmp(descriptor.name, binary_name) == 0) {
                        type_id = descriptor.type_id;
                        is_enum = descriptor.is_enum;
                        break;
                    }
                }
            }
            javan_runtime_class_state* state = (javan_runtime_class_state*) javan_alloc(sizeof(javan_runtime_class_state));
            state->magic = JAVAN_RUNTIME_CLASS_MAGIC;
            state->type_id = type_id;
            state->is_enum = is_enum;
            state->reserved0 = 0;
            state->binary_name = binary_name;
            javan_update_runtime_allocation_kind((void*) state, JAVAN_RUNTIME_KIND_CLASS);
            return state;
        }

        void* javan_runtime_class_literal(void* binary_name) {
            return javan_runtime_class_new((const char*) binary_name);
        }

        void* javan_throwable_new(const char* binary_name) {
            if (binary_name == NULL || binary_name[0] == '\\0') {
                javan_panic("invalid Throwable type");
            }
            javan_throwable_value* throwable = (javan_throwable_value*) javan_alloc(sizeof(javan_throwable_value));
            throwable->magic = JAVAN_THROWABLE_MAGIC;
            throwable->reserved0 = 0;
            throwable->reserved1 = 0;
            throwable->reserved2 = 0;
            throwable->binary_name = binary_name;
            throwable->message = NULL;
            throwable->suppressed = NULL;
            throwable->stack_trace = NULL;
            javan_update_runtime_allocation_kind((void*) throwable, JAVAN_RUNTIME_KIND_THROWABLE);
            return (void*) throwable;
        }

        void* javan_throwable_new_with_message(const char* binary_name, void* message) {
            void* throwable = javan_throwable_new(binary_name);
            javan_throwable_set_message(throwable, message);
            return throwable;
        }

        void javan_throwable_set_message(void* throwable_value, void* message) {
            void* throwable_root = throwable_value;
            void* message_root = message;
            void* copied_message = NULL;
            void** javan_throwable_message_roots[] = {
                (void**) &throwable_root,
                (void**) &message_root,
                (void**) &copied_message
            };
            javan_root_frame_push(javan_throwable_message_roots, 3);
            javan_throwable_value* throwable = javan_throwable_checked(throwable_root);
            if (message_root == NULL) {
                throwable->message = NULL;
                javan_root_frame_pop(javan_throwable_message_roots);
                return;
            }
            copied_message = javan_string_from((const char*) message_root);
            throwable->message = copied_message;
            javan_root_frame_pop(javan_throwable_message_roots);
        }

        void* javan_throwable_get_message(void* throwable_value) {
            return javan_throwable_checked(throwable_value)->message;
        }

        void javan_throwable_add_suppressed(void* throwable_value, void* suppressed_value) {
            javan_throwable_value* throwable = javan_throwable_checked(throwable_value);
            if (suppressed_value == NULL) {
                javan_panic("Throwable.addSuppressed null");
            }
            if (suppressed_value == throwable_value) {
                javan_panic("Throwable.addSuppressed self");
            }
            if (throwable->suppressed == NULL) {
                void** javan_throwable_roots[] = {
                    (void**) &throwable_value
                };
                javan_root_frame_push(javan_throwable_roots, 1);
                throwable->suppressed = javan_list_new_with_capacity(1, 0);
                javan_root_frame_pop(javan_throwable_roots);
            }
            javan_list_append_raw(throwable->suppressed, suppressed_value);
        }

        void* javan_throwable_get_suppressed(void* throwable_value) {
            javan_throwable_value* throwable = javan_throwable_checked(throwable_value);
            if (throwable->suppressed == NULL || throwable->suppressed->length == 0) {
                return javan_object_array_new(0);
            }
            void* result = javan_object_array_new(throwable->suppressed->length);
            for (int index = 0; index < throwable->suppressed->length; index++) {
                javan_object_array_set(result, index, throwable->suppressed->values[index]);
            }
            return result;
        }

        void* javan_throwable_get_stack_trace(void* throwable_value) {
            void* throwable_root = throwable_value;
            void* result_root = NULL;
            void** javan_throwable_stack_trace_roots[] = {
                (void**) &throwable_root,
                (void**) &result_root
            };
            javan_root_frame_push(javan_throwable_stack_trace_roots, 2);
            javan_throwable_value* throwable = javan_throwable_checked(throwable_root);
            if (throwable->stack_trace == NULL) {
                result_root = javan_object_array_new(0);
                javan_root_frame_pop(javan_throwable_stack_trace_roots);
                return result_root;
            }
            int length = javan_array_length(throwable->stack_trace);
            result_root = javan_object_array_new(length);
            for (int index = 0; index < length; index++) {
                javan_object_array_set(result_root, index, javan_object_array_get(throwable->stack_trace, index));
            }
            javan_root_frame_pop(javan_throwable_stack_trace_roots);
            return result_root;
        }

        void javan_throwable_set_stack_trace(void* throwable_value, void* stack_trace_value) {
            if (stack_trace_value == NULL) {
                javan_panic("Throwable.setStackTrace null");
            }
            void* throwable_root = throwable_value;
            void* stack_trace_root = stack_trace_value;
            void* copy_root = NULL;
            void** javan_throwable_stack_trace_roots[] = {
                (void**) &throwable_root,
                (void**) &stack_trace_root,
                (void**) &copy_root
            };
            javan_root_frame_push(javan_throwable_stack_trace_roots, 3);
            int length = javan_array_length(stack_trace_root);
            copy_root = javan_object_array_new(length);
            for (int index = 0; index < length; index++) {
                void* element = javan_object_array_get(stack_trace_root, index);
                if (element == NULL) {
                    javan_root_frame_pop(javan_throwable_stack_trace_roots);
                    javan_panic("Throwable.setStackTrace null element");
                }
                javan_object_array_set(copy_root, index, element);
            }
            javan_throwable_checked(throwable_root)->stack_trace = copy_root;
            javan_root_frame_pop(javan_throwable_stack_trace_roots);
        }

        const char* javan_panic_detail_object(void* value) {
            if (value == NULL) {
                return NULL;
            }
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                return NULL;
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                return (const char*) value;
            }
            if (node->runtime_kind == JAVAN_RUNTIME_KIND_THROWABLE) {
                return (const char*) javan_throwable_checked(value)->message;
            }
            return NULL;
        }

        void* javan_atomic_boolean_new(void) {
            javan_atomic_boolean* state = (javan_atomic_boolean*) javan_alloc(sizeof(javan_atomic_boolean));
            state->magic = JAVAN_ATOMIC_BOOLEAN_MAGIC;
            state->value = 0;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) state, JAVAN_RUNTIME_KIND_ATOMIC_BOOLEAN);
            return state;
        }

        void javan_atomic_boolean_init(void* value, int initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_boolean_checked(value)->value = initial_value == 0 ? 0 : 1;
            javan_runtime_lock_leave();
        }

        int javan_atomic_boolean_get(void* value) {
            javan_runtime_lock_enter();
            int result = javan_atomic_boolean_checked(value)->value == 0 ? 0 : 1;
            javan_runtime_lock_leave();
            return result;
        }

        int javan_atomic_boolean_get_plain(void* value) {
            return javan_atomic_boolean_get(value);
        }

        void javan_atomic_boolean_set(void* value, int next_value) {
            javan_runtime_lock_enter();
            javan_atomic_boolean_checked(value)->value = next_value == 0 ? 0 : 1;
            javan_runtime_lock_leave();
        }

        int javan_atomic_boolean_compare_and_set(void* value, int expected_value, int next_value) {
            javan_runtime_lock_enter();
            javan_atomic_boolean* state = javan_atomic_boolean_checked(value);
            int expected = expected_value == 0 ? 0 : 1;
            if (state->value != expected) {
                javan_runtime_lock_leave();
                return 0;
            }
            state->value = next_value == 0 ? 0 : 1;
            javan_runtime_lock_leave();
            return 1;
        }

        void* javan_atomic_integer_new(void) {
            javan_atomic_integer* state = (javan_atomic_integer*) javan_alloc(sizeof(javan_atomic_integer));
            state->magic = JAVAN_ATOMIC_INTEGER_MAGIC;
            state->value = 0;
            state->reserved0 = 0;
            state->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) state, JAVAN_RUNTIME_KIND_ATOMIC_INTEGER);
            return state;
        }

        void javan_atomic_integer_init(void* value, int initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_integer_checked(value)->value = initial_value;
            javan_runtime_lock_leave();
        }

        int javan_atomic_integer_get(void* value) {
            javan_runtime_lock_enter();
            int result = javan_atomic_integer_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        int javan_atomic_integer_compare_and_set(void* value, int expected_value, int next_value) {
            javan_runtime_lock_enter();
            javan_atomic_integer* state = javan_atomic_integer_checked(value);
            if (state->value != expected_value) {
                javan_runtime_lock_leave();
                return 0;
            }
            state->value = next_value;
            javan_runtime_lock_leave();
            return 1;
        }

        int javan_atomic_integer_get_and_increment(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_integer* state = javan_atomic_integer_checked(value);
            int current = state->value;
            state->value = (int) ((unsigned int) current + 1u);
            javan_runtime_lock_leave();
            return current;
        }

        int javan_atomic_integer_increment_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_integer* state = javan_atomic_integer_checked(value);
            state->value = (int) ((unsigned int) state->value + 1u);
            int result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        int javan_atomic_integer_decrement_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_integer* state = javan_atomic_integer_checked(value);
            state->value = (int) ((unsigned int) state->value - 1u);
            int result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        void* javan_atomic_long_new(void) {
            javan_atomic_long* state = (javan_atomic_long*) javan_alloc(sizeof(javan_atomic_long));
            state->magic = JAVAN_ATOMIC_LONG_MAGIC;
            state->reserved0 = 0;
            state->value = 0;
            javan_update_runtime_allocation_kind((void*) state, JAVAN_RUNTIME_KIND_ATOMIC_LONG);
            return state;
        }

        void javan_atomic_long_init(void* value, long long initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_long_checked(value)->value = initial_value;
            javan_runtime_lock_leave();
        }

        long long javan_atomic_long_get(void* value) {
            javan_runtime_lock_enter();
            long long result = javan_atomic_long_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        long long javan_atomic_long_increment_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_long* state = javan_atomic_long_checked(value);
            state->value = (long long) ((unsigned long long) state->value + 1ull);
            long long result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        long long javan_atomic_long_decrement_and_get(void* value) {
            javan_runtime_lock_enter();
            javan_atomic_long* state = javan_atomic_long_checked(value);
            state->value = (long long) ((unsigned long long) state->value - 1ull);
            long long result = state->value;
            javan_runtime_lock_leave();
            return result;
        }

        void* javan_atomic_reference_new(void) {
            javan_atomic_reference* state = (javan_atomic_reference*) javan_alloc(sizeof(javan_atomic_reference));
            state->magic = JAVAN_ATOMIC_REFERENCE_MAGIC;
            state->reserved0 = 0;
            state->reserved1 = 0;
            state->reserved2 = 0;
            state->value = NULL;
            javan_update_runtime_allocation_kind((void*) state, JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE);
            return state;
        }

        void javan_atomic_reference_init(void* value, void* initial_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference_checked(value)->value = initial_value;
            javan_runtime_lock_leave();
        }

        void* javan_atomic_reference_get(void* value) {
            javan_runtime_lock_enter();
            void* result = javan_atomic_reference_checked(value)->value;
            javan_runtime_lock_leave();
            return result;
        }

        void javan_atomic_reference_set(void* value, void* next_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference_checked(value)->value = next_value;
            javan_runtime_lock_leave();
        }

        int javan_atomic_reference_compare_and_set(void* value, void* expected_value, void* next_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference* state = javan_atomic_reference_checked(value);
            if (state->value != expected_value) {
                javan_runtime_lock_leave();
                return 0;
            }
            state->value = next_value;
            javan_runtime_lock_leave();
            return 1;
        }

        void* javan_atomic_reference_get_and_set(void* value, void* next_value) {
            javan_runtime_lock_enter();
            javan_atomic_reference* state = javan_atomic_reference_checked(value);
            void* previous = state->value;
            state->value = next_value;
            javan_runtime_lock_leave();
            return previous;
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

        void* javan_object_get_class(void* value) {
            if (value == NULL) {
                javan_panic("Object.getClass on null");
            }
            const char* binary_name = NULL;
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                binary_name = "java.lang.String";
            } else if (node->kind == JAVAN_HEAP_KIND_ARRAY) {
                binary_name = javan_runtime_array_binary_name(node->type_id);
            } else if (node->kind == JAVAN_HEAP_KIND_OBJECT) {
                JavanTypeDescriptor* descriptor = javan_type_descriptor_for(node->type_id);
                if (descriptor != NULL) {
                    binary_name = descriptor->name;
                }
            } else if (node->kind == JAVAN_HEAP_KIND_RUNTIME) {
                binary_name = javan_runtime_kind_binary_name(node->runtime_kind);
            }
            javan_runtime_lock_leave();
            if (binary_name == NULL) {
                javan_panic("unsupported Object.getClass receiver");
            }
            return javan_runtime_class_new(binary_name);
        }

        void* javan_runtime_class_get_name(void* value) {
            return javan_string_from(javan_runtime_class_checked(value)->binary_name);
        }

        void* javan_runtime_class_get_simple_name(void* value) {
            const char* binary_name = javan_runtime_class_checked(value)->binary_name;
            if (javan_runtime_class_name_is_array(binary_name) != 0) {
                return javan_string_from(binary_name);
            }
            return javan_string_from(javan_runtime_class_simple_name_source(binary_name));
        }

        int javan_runtime_class_is_array(void* value) {
            return javan_runtime_class_name_is_array(javan_runtime_class_checked(value)->binary_name);
        }

        int javan_runtime_class_is_enum(void* value) {
            return javan_runtime_class_checked(value)->is_enum != 0;
        }

        int javan_runtime_class_is_instance(void* type_value, void* value) {
            return javan_runtime_class_matches_value(javan_runtime_class_checked(type_value), value);
        }

        void* javan_runtime_class_cast(void* type_value, void* value) {
            if (value == NULL) {
                return NULL;
            }
            if (javan_runtime_class_is_instance(type_value, value) != 0) {
                return value;
            }
            javan_panic("Class.cast type mismatch");
            return NULL;
        }

        int javan_runtime_class_is_assignable_from(void* target_type, void* source_type) {
            javan_runtime_class_state* target = javan_runtime_class_checked(target_type);
            javan_runtime_class_state* source = javan_runtime_class_checked(source_type);
            return javan_runtime_class_matches_class(target, source);
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

    private static final String SOURCE_HEAP_ALLOC_EXECUTOR_CONT = """
        void* javan_virtual_thread_executor_submit(void* value, void* runnable) {
            void* executor_root = value;
            void* runnable_root = runnable;
            void* thread_value = NULL;
            void* future_value = NULL;
            void** roots[] = {
                (void**) &executor_root,
                (void**) &runnable_root,
                (void**) &thread_value,
                (void**) &future_value
            };
            javan_root_frame_push(roots, 4);
            javan_virtual_thread_executor_state* state = javan_virtual_thread_executor_checked(executor_root);
            if (state->closed != 0) {
                javan_panic("virtual thread executor is closed");
            }
            javan_profile_executor_execute_calls_value++;
            thread_value = javan_virtual_thread_factory_new_thread(state->factory, runnable_root);
            future_value = javan_alloc(sizeof(javan_future_value));
            javan_future_value* future = (javan_future_value*) future_value;
            future->magic = JAVAN_FUTURE_MAGIC;
            future->cancelled = 0;
            future->reserved0 = 0;
            future->reserved1 = 0;
            future->thread = thread_value;
            javan_update_runtime_allocation_kind(future_value, JAVAN_RUNTIME_KIND_FUTURE);
            javan_thread_start(thread_value);
            javan_list_append_raw(state->threads, thread_value);
            javan_root_frame_pop(roots);
            return future_value;
        }

        int javan_future_cancel(void* value, int may_interrupt_if_running) {
            javan_future_value* future = javan_future_checked(value);
            if (future->thread == NULL || future->cancelled != 0 || may_interrupt_if_running == 0) {
                return 0;
            }
            javan_thread* thread = javan_require_thread(future->thread);
            javan_runtime_lock_enter();
            int cancellable = thread->completed == 0;
            if (cancellable != 0) {
                future->cancelled = 1;
                thread->interrupted = 1;
            }
            javan_runtime_lock_leave();
            return cancellable;
        }

        void* javan_count_down_latch_new(void) {
            void* value = javan_alloc(sizeof(javan_count_down_latch_value));
            javan_count_down_latch_value* latch = (javan_count_down_latch_value*) value;
            latch->magic = JAVAN_COUNT_DOWN_LATCH_MAGIC;
            latch->count = 0;
            latch->reserved0 = 0;
            latch->reserved1 = 0;
            javan_update_runtime_allocation_kind(value, JAVAN_RUNTIME_KIND_COUNT_DOWN_LATCH);
            return value;
        }

        void javan_count_down_latch_init(void* value, int count) {
            if (count < 0) {
                javan_panic("negative CountDownLatch count");
            }
            javan_count_down_latch_checked(value)->count = count;
        }

        int javan_count_down_latch_await_timeout(void* value, long long timeout_millis) {
            javan_count_down_latch_value* latch = javan_count_down_latch_checked(value);
            if (timeout_millis < 0LL) {
                javan_panic("negative CountDownLatch timeout");
            }
            long long deadline = javan_system_current_time_millis() + timeout_millis;
            while (1) {
                javan_runtime_lock_enter();
                int count = latch->count;
                javan_runtime_lock_leave();
                if (count <= 0) {
                    return 1;
                }
                if (javan_thread_current_interrupted_peek() != 0) {
                    (void) javan_thread_interrupted();
                    return -1;
                }
                if (javan_system_current_time_millis() >= deadline) {
                    return 0;
                }
                javan_sleep_micros(1000UL);
            }
        }

        void javan_count_down_latch_count_down(void* value) {
            javan_count_down_latch_value* latch = javan_count_down_latch_checked(value);
            javan_runtime_lock_enter();
            if (latch->count > 0) {
                latch->count--;
            }
            javan_runtime_lock_leave();
        }

        long long javan_count_down_latch_get_count(void* value) {
            javan_count_down_latch_value* latch = javan_count_down_latch_checked(value);
            javan_runtime_lock_enter();
            long long count = latch->count;
            javan_runtime_lock_leave();
            return count;
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

        void* javan_character_value_of(int value) {
            javan_boxed_character* object = (javan_boxed_character*) javan_alloc(sizeof(javan_boxed_character));
            object->value = value & 0xffff;
            javan_register_object((void*) object, JAVAN_TYPE_JAVA_LANG_CHARACTER);
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

        int javan_number_int_value(void* value) {
            int type_id = javan_registered_type_id(value);
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return ((javan_boxed_int*) value)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return (int) ((javan_boxed_long*) value)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return (int) ((javan_boxed_float*) value)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return (int) ((javan_boxed_double*) value)->value;
            }
            javan_panic("not a supported Number");
            return 0;
        }

        long long javan_number_long_value(void* value) {
            int type_id = javan_registered_type_id(value);
            if (type_id == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                return (long long) ((javan_boxed_int*) value)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_LONG) {
                return ((javan_boxed_long*) value)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return (long long) ((javan_boxed_float*) value)->value;
            }
            if (type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return (long long) ((javan_boxed_double*) value)->value;
            }
            javan_panic("not a supported Number");
            return 0;
        }

        int javan_character_char_value(void* value) {
            if (javan_registered_type_id(value) != JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                javan_panic("not a Character");
            }
            return ((javan_boxed_character*) value)->value;
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

        long long javan_long_parse_long(void* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            const char* text = (const char*) value;
            if (*text != '\\0' && isspace((unsigned char) *text) != 0) {
                javan_panic("invalid long");
            }
            errno = 0;
            char* end = NULL;
            long long result = strtoll(text, &end, 10);
            if (text == end) {
                javan_panic("invalid long");
            }
            if (errno == ERANGE) {
                javan_panic("long out of range");
            }
            if (end == NULL || *end != '\\0') {
                javan_panic("invalid long");
            }
            return result;
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

        double javan_double_parse_double(void* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            const char* text = (const char*) value;
            while (*text != '\\0' && isspace((unsigned char) *text) != 0) {
                text++;
            }
            char* end = NULL;
            double result = strtod(text, &end);
            if (text == end) {
                javan_panic("invalid double");
            }
            while (end != NULL && *end != '\\0' && isspace((unsigned char) *end) != 0) {
                end++;
            }
            if (end == NULL || *end != '\\0') {
                javan_panic("invalid double");
            }
            return result;
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
        """;

    private static final String SOURCE_HEAP_ALLOC_TIME = """

        static int javan_epoch_seconds_and_millis(long long epoch_millis, time_t* epoch_seconds, int* millis_part) {
            if (epoch_seconds == NULL || millis_part == NULL) {
                return 0;
            }
            long long seconds = epoch_millis / 1000LL;
            int millis = (int) (epoch_millis % 1000LL);
            if (millis < 0) {
                millis += 1000;
                seconds -= 1;
            }
            *epoch_seconds = (time_t) seconds;
            *millis_part = millis;
            return ((long long) *epoch_seconds) == seconds;
        }

        static int javan_localtime_portable(time_t value, struct tm* result) {
            #if defined(_WIN32)
            return localtime_s(result, &value) == 0;
            #else
            return localtime_r(&value, result) != NULL;
            #endif
        }

        static int javan_gmtime_portable(time_t value, struct tm* result) {
            #if defined(_WIN32)
            return gmtime_s(result, &value) == 0;
            #else
            return gmtime_r(&value, result) != NULL;
            #endif
        }

        static int javan_timegm_portable(struct tm* value, time_t* result) {
            if (value == NULL || result == NULL) {
                return 0;
            }
            #if defined(_WIN32)
            time_t converted = _mkgmtime(value);
            #else
            time_t converted = timegm(value);
            #endif
            if (converted == (time_t) -1) {
                return 0;
            }
            *result = converted;
            return 1;
        }

        static int javan_system_default_offset_seconds(long long epoch_millis) {
            time_t epoch_seconds = 0;
            int millis_part = 0;
            struct tm local_calendar;
            time_t utc_as_local = 0;
            if (javan_epoch_seconds_and_millis(epoch_millis, &epoch_seconds, &millis_part) == 0
                || javan_localtime_portable(epoch_seconds, &local_calendar) == 0
                || javan_timegm_portable(&local_calendar, &utc_as_local) == 0) {
                javan_panic("time conversion failed");
            }
            long long offset = (long long) utc_as_local - (long long) epoch_seconds;
            if (offset > INT_MAX || offset < INT_MIN) {
                javan_panic("zone offset overflow");
            }
            return (int) offset;
        }

        static void* javan_zone_id_new_system_default(void) {
            javan_zone_id_value* zone = (javan_zone_id_value*) javan_alloc(sizeof(javan_zone_id_value));
            zone->magic = JAVAN_ZONE_ID_MAGIC;
            zone->kind = JAVAN_ZONE_KIND_SYSTEM_DEFAULT;
            zone->reserved0 = 0;
            zone->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) zone, JAVAN_RUNTIME_KIND_ZONE_ID);
            return (void*) zone;
        }

        static void* javan_zone_offset_new_fixed(int total_seconds) {
            javan_zone_id_value* zone = (javan_zone_id_value*) javan_alloc(sizeof(javan_zone_id_value));
            zone->magic = JAVAN_ZONE_ID_MAGIC;
            zone->kind = JAVAN_ZONE_KIND_FIXED_OFFSET;
            zone->reserved0 = total_seconds;
            zone->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) zone, JAVAN_RUNTIME_KIND_ZONE_ID);
            return (void*) zone;
        }

        static void* javan_instant_new(long long epoch_millis) {
            javan_instant_value* instant = (javan_instant_value*) javan_alloc(sizeof(javan_instant_value));
            instant->magic = JAVAN_INSTANT_MAGIC;
            instant->reserved0 = 0;
            instant->reserved1 = 0;
            instant->reserved2 = 0;
            instant->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) instant, JAVAN_RUNTIME_KIND_INSTANT);
            return (void*) instant;
        }

        static void* javan_date_new(long long epoch_millis) {
            javan_date_value* date = (javan_date_value*) javan_alloc(sizeof(javan_date_value));
            date->magic = JAVAN_DATE_MAGIC;
            date->reserved0 = 0;
            date->reserved1 = 0;
            date->reserved2 = 0;
            date->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) date, JAVAN_RUNTIME_KIND_DATE);
            return (void*) date;
        }

        static void* javan_sql_date_new(long long epoch_millis) {
            javan_sql_date_value* date = (javan_sql_date_value*) javan_alloc(sizeof(javan_sql_date_value));
            date->magic = JAVAN_SQL_DATE_MAGIC;
            date->reserved0 = 0;
            date->reserved1 = 0;
            date->reserved2 = 0;
            date->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) date, JAVAN_RUNTIME_KIND_SQL_DATE);
            return (void*) date;
        }

        static void* javan_sql_time_new(long long epoch_millis) {
            javan_sql_time_value* time = (javan_sql_time_value*) javan_alloc(sizeof(javan_sql_time_value));
            time->magic = JAVAN_SQL_TIME_MAGIC;
            time->reserved0 = 0;
            time->reserved1 = 0;
            time->reserved2 = 0;
            time->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) time, JAVAN_RUNTIME_KIND_SQL_TIME);
            return (void*) time;
        }

        static void* javan_sql_timestamp_new(long long epoch_millis) {
            javan_sql_timestamp_value* timestamp = (javan_sql_timestamp_value*) javan_alloc(sizeof(javan_sql_timestamp_value));
            timestamp->magic = JAVAN_SQL_TIMESTAMP_MAGIC;
            timestamp->reserved0 = 0;
            timestamp->reserved1 = 0;
            timestamp->reserved2 = 0;
            timestamp->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) timestamp, JAVAN_RUNTIME_KIND_SQL_TIMESTAMP);
            return (void*) timestamp;
        }

        static void* javan_local_date_new(int year, int month, int day) {
            javan_local_date_value* date = (javan_local_date_value*) javan_alloc(sizeof(javan_local_date_value));
            date->magic = JAVAN_LOCAL_DATE_MAGIC;
            date->year = year;
            date->month = month;
            date->day = day;
            javan_update_runtime_allocation_kind((void*) date, JAVAN_RUNTIME_KIND_LOCAL_DATE);
            return (void*) date;
        }

        static void* javan_local_time_new(int hour, int minute, int second, int millis) {
            javan_local_time_value* time = (javan_local_time_value*) javan_alloc(sizeof(javan_local_time_value));
            time->magic = JAVAN_LOCAL_TIME_MAGIC;
            time->hour = hour;
            time->minute = minute;
            time->second = second;
            time->millis = millis;
            javan_update_runtime_allocation_kind((void*) time, JAVAN_RUNTIME_KIND_LOCAL_TIME);
            return (void*) time;
        }

        static void* javan_local_date_time_new(int year, int month, int day, int hour, int minute, int second, int millis) {
            javan_local_date_time_value* date_time = (javan_local_date_time_value*) javan_alloc(sizeof(javan_local_date_time_value));
            date_time->magic = JAVAN_LOCAL_DATE_TIME_MAGIC;
            date_time->year = year;
            date_time->month = month;
            date_time->day = day;
            date_time->hour = hour;
            date_time->minute = minute;
            date_time->second = second;
            date_time->millis = millis;
            javan_update_runtime_allocation_kind((void*) date_time, JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME);
            return (void*) date_time;
        }

        static void* javan_zoned_date_time_new(long long epoch_millis) {
            javan_zoned_date_time_value* date_time = (javan_zoned_date_time_value*) javan_alloc(sizeof(javan_zoned_date_time_value));
            date_time->magic = JAVAN_ZONED_DATE_TIME_MAGIC;
            date_time->zone_kind = JAVAN_ZONE_KIND_SYSTEM_DEFAULT;
            date_time->reserved0 = 0;
            date_time->reserved1 = 0;
            date_time->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) date_time, JAVAN_RUNTIME_KIND_ZONED_DATE_TIME);
            return (void*) date_time;
        }

        static void* javan_offset_date_time_new(long long epoch_millis) {
            javan_offset_date_time_value* date_time = (javan_offset_date_time_value*) javan_alloc(sizeof(javan_offset_date_time_value));
            date_time->magic = JAVAN_OFFSET_DATE_TIME_MAGIC;
            date_time->zone_kind = JAVAN_ZONE_KIND_SYSTEM_DEFAULT;
            date_time->reserved0 = 0;
            date_time->reserved1 = 0;
            date_time->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) date_time, JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME);
            return (void*) date_time;
        }

        static void* javan_calendar_new(long long epoch_millis) {
            javan_calendar_value* calendar = (javan_calendar_value*) javan_alloc(sizeof(javan_calendar_value));
            calendar->magic = JAVAN_CALENDAR_MAGIC;
            calendar->reserved0 = 0;
            calendar->reserved1 = 0;
            calendar->reserved2 = 0;
            calendar->epoch_millis = epoch_millis;
            javan_update_runtime_allocation_kind((void*) calendar, JAVAN_RUNTIME_KIND_CALENDAR);
            return (void*) calendar;
        }

        static long long javan_date_like_epoch_millis(void* value) {
            switch (javan_runtime_kind_of(value)) {
                case JAVAN_RUNTIME_KIND_DATE:
                    return javan_date_checked(value)->epoch_millis;
                case JAVAN_RUNTIME_KIND_SQL_DATE:
                    return javan_sql_date_checked(value)->epoch_millis;
                case JAVAN_RUNTIME_KIND_SQL_TIME:
                    return javan_sql_time_checked(value)->epoch_millis;
                case JAVAN_RUNTIME_KIND_SQL_TIMESTAMP:
                    return javan_sql_timestamp_checked(value)->epoch_millis;
                default:
                    javan_panic("unsupported date");
            }
            return 0;
        }

        static void javan_components_from_epoch_millis_local(
            long long epoch_millis,
            int* year,
            int* month,
            int* day,
            int* hour,
            int* minute,
            int* second,
            int* millis
        ) {
            time_t epoch_seconds = 0;
            int millis_part = 0;
            struct tm calendar;
            if (javan_epoch_seconds_and_millis(epoch_millis, &epoch_seconds, &millis_part) == 0
                || javan_localtime_portable(epoch_seconds, &calendar) == 0) {
                javan_panic("time conversion failed");
            }
            *year = calendar.tm_year + 1900;
            *month = calendar.tm_mon + 1;
            *day = calendar.tm_mday;
            *hour = calendar.tm_hour;
            *minute = calendar.tm_min;
            *second = calendar.tm_sec;
            *millis = millis_part;
        }

        static long long javan_epoch_millis_from_local_components(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int millis
        ) {
            struct tm calendar;
            memset(&calendar, 0, sizeof(calendar));
            calendar.tm_year = year - 1900;
            calendar.tm_mon = month - 1;
            calendar.tm_mday = day;
            calendar.tm_hour = hour;
            calendar.tm_min = minute;
            calendar.tm_sec = second;
            calendar.tm_isdst = -1;
            time_t epoch_seconds = mktime(&calendar);
            if (epoch_seconds == (time_t) -1) {
                javan_panic("time conversion failed");
            }
            return ((long long) epoch_seconds * 1000LL) + millis;
        }

        void* javan_zone_id_system_default(void) {
            return javan_zone_id_new_system_default();
        }

        void* javan_instant_now(void) {
            return javan_instant_new(javan_system_current_time_millis());
        }

        void* javan_instant_of_epoch_millis(long long millis) {
            return javan_instant_new(millis);
        }

        void* javan_instant_from_temporal(void* value) {
            int runtime_kind = javan_runtime_kind_of(value);
            if (runtime_kind == JAVAN_RUNTIME_KIND_INSTANT) {
                return value;
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                return javan_zoned_date_time_to_instant(value);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                return javan_offset_date_time_to_instant(value);
            }
            javan_panic("unsupported Instant.from temporal");
            return NULL;
        }

        long long javan_instant_to_epoch_millis(void* value) {
            return javan_instant_checked(value)->epoch_millis;
        }

        void* javan_instant_at_zone(void* instant, void* zone) {
            javan_zone_id_checked(zone);
            return javan_zoned_date_time_new(javan_instant_checked(instant)->epoch_millis);
        }

        int javan_temporal_accessor_is_supported(void* temporal, void* field) {
            const char* field_name = javan_charsequence_string_value(field);
            int runtime_kind = javan_runtime_kind_of(temporal);
            if (strcmp(field_name, "INSTANT_SECONDS") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_INSTANT
                    || runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME;
            }
            if (strcmp(field_name, "OFFSET_SECONDS") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME;
            }
            if (strcmp(field_name, "EPOCH_DAY") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE
                    || runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME;
            }
            if (strcmp(field_name, "NANO_OF_DAY") == 0) {
                return runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME;
            }
            javan_panic("unsupported temporal field");
            return 0;
        }

        void* javan_temporal_accessor_query(void* temporal, void* query) {
            const char* query_name = javan_charsequence_string_value(query);
            int runtime_kind = javan_runtime_kind_of(temporal);
            if (strcmp(query_name, "zone") == 0) {
                if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME
                    || runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                    return javan_zone_id_new_system_default();
                }
                return NULL;
            }
            if (strcmp(query_name, "localDate") == 0) {
                if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE) {
                    return temporal;
                }
                if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                    return javan_local_date_time_to_local_date(temporal);
                }
                if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                    return javan_zoned_date_time_to_local_date(temporal);
                }
                if (runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                    return javan_local_date_from_temporal(temporal);
                }
                return NULL;
            }
            if (strcmp(query_name, "localTime") == 0) {
                if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_TIME) {
                    return temporal;
                }
                if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                    return javan_local_date_time_to_local_time(temporal);
                }
                if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                    return javan_zoned_date_time_to_local_time(temporal);
                }
                if (runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                    return javan_local_time_from_temporal(temporal);
                }
                return NULL;
            }
            javan_panic("unsupported temporal query");
            return NULL;
        }

        void* javan_local_date_of_epoch_day(long long epoch_day) {
            if (epoch_day > LLONG_MAX / 86400LL || epoch_day < LLONG_MIN / 86400LL) {
                javan_panic("epoch day overflow");
            }
            time_t epoch_seconds = (time_t) (epoch_day * 86400LL);
            if (((long long) epoch_seconds) != epoch_day * 86400LL) {
                javan_panic("epoch day overflow");
            }
            struct tm calendar;
            if (javan_gmtime_portable(epoch_seconds, &calendar) == 0) {
                javan_panic("time conversion failed");
            }
            return javan_local_date_new(calendar.tm_year + 1900, calendar.tm_mon + 1, calendar.tm_mday);
        }

        void* javan_local_date_from_temporal(void* temporal) {
            int runtime_kind = javan_runtime_kind_of(temporal);
            if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE) {
                return temporal;
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                return javan_local_date_time_to_local_date(temporal);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                return javan_zoned_date_time_to_local_date(temporal);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                int year = 0;
                int month = 0;
                int day = 0;
                int hour = 0;
                int minute = 0;
                int second = 0;
                int millis = 0;
                javan_components_from_epoch_millis_local(
                    javan_offset_date_time_checked(temporal)->epoch_millis,
                    &year,
                    &month,
                    &day,
                    &hour,
                    &minute,
                    &second,
                    &millis
                );
                return javan_local_date_new(year, month, day);
            }
            javan_panic("unsupported LocalDate.from temporal");
            return NULL;
        }

        void* javan_local_date_now(void* zone) {
            javan_zone_id_checked(zone);
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_system_current_time_millis(),
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_date_new(year, month, day);
        }

        void* javan_local_date_at_start_of_day(void* value) {
            javan_local_date_value* date = javan_local_date_checked(value);
            return javan_local_date_time_new(date->year, date->month, date->day, 0, 0, 0, 0);
        }

        void* javan_local_date_at_start_of_day_zone(void* value, void* zone) {
            javan_local_date_value* date = javan_local_date_checked(value);
            javan_zone_id_checked(zone);
            return javan_zoned_date_time_new(javan_epoch_millis_from_local_components(
                date->year,
                date->month,
                date->day,
                0,
                0,
                0,
                0
            ));
        }

        void* javan_local_time_midnight(void) {
            return javan_local_time_new(0, 0, 0, 0);
        }

        void* javan_local_time_from_temporal(void* temporal) {
            int runtime_kind = javan_runtime_kind_of(temporal);
            if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_TIME) {
                return temporal;
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                return javan_local_date_time_to_local_time(temporal);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                return javan_zoned_date_time_to_local_time(temporal);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                int year = 0;
                int month = 0;
                int day = 0;
                int hour = 0;
                int minute = 0;
                int second = 0;
                int millis = 0;
                javan_components_from_epoch_millis_local(
                    javan_offset_date_time_checked(temporal)->epoch_millis,
                    &year,
                    &month,
                    &day,
                    &hour,
                    &minute,
                    &second,
                    &millis
                );
                return javan_local_time_new(hour, minute, second, millis);
            }
            javan_panic("unsupported LocalTime.from temporal");
            return NULL;
        }

        int javan_local_time_get_hour(void* value) {
            return javan_local_time_checked(value)->hour;
        }

        int javan_local_time_get_minute(void* value) {
            return javan_local_time_checked(value)->minute;
        }

        int javan_local_time_get_second(void* value) {
            return javan_local_time_checked(value)->second;
        }

        int javan_local_time_get_nano(void* value) {
            return javan_local_time_checked(value)->millis * 1000000;
        }

        void* javan_local_date_time_of_instant(void* instant, void* zone) {
            javan_zone_id_checked(zone);
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_instant_checked(instant)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_date_time_new(year, month, day, hour, minute, second, millis);
        }

        void* javan_local_date_time_at_zone(void* value, void* zone) {
            javan_local_date_time_value* date_time = javan_local_date_time_checked(value);
            javan_zone_id_checked(zone);
            return javan_zoned_date_time_new(javan_epoch_millis_from_local_components(
                date_time->year,
                date_time->month,
                date_time->day,
                date_time->hour,
                date_time->minute,
                date_time->second,
                date_time->millis
            ));
        }

        void* javan_local_date_time_to_local_date(void* value) {
            javan_local_date_time_value* date_time = javan_local_date_time_checked(value);
            return javan_local_date_new(date_time->year, date_time->month, date_time->day);
        }

        void* javan_local_date_time_to_local_time(void* value) {
            javan_local_date_time_value* date_time = javan_local_date_time_checked(value);
            return javan_local_time_new(date_time->hour, date_time->minute, date_time->second, date_time->millis);
        }

        void* javan_zoned_date_time_now(void) {
            return javan_zoned_date_time_new(javan_system_current_time_millis());
        }

        void* javan_zoned_date_time_now_zone(void* zone) {
            javan_zone_id_checked(zone);
            return javan_zoned_date_time_new(javan_system_current_time_millis());
        }

        void* javan_zoned_date_time_of(void* date, void* time, void* zone) {
            javan_local_date_value* local_date = javan_local_date_checked(date);
            javan_local_time_value* local_time = javan_local_time_checked(time);
            javan_zone_id_checked(zone);
            return javan_zoned_date_time_new(javan_epoch_millis_from_local_components(
                local_date->year,
                local_date->month,
                local_date->day,
                local_time->hour,
                local_time->minute,
                local_time->second,
                local_time->millis
            ));
        }

        void* javan_zoned_date_time_get_offset(void* value) {
            return javan_zone_offset_new_fixed(
                javan_system_default_offset_seconds(javan_zoned_date_time_checked(value)->epoch_millis)
            );
        }

        void* javan_zoned_date_time_to_instant(void* value) {
            return javan_instant_new(javan_zoned_date_time_checked(value)->epoch_millis);
        }

        void* javan_zoned_date_time_from_temporal(void* value) {
            int runtime_kind = javan_runtime_kind_of(value);
            if (runtime_kind == JAVAN_RUNTIME_KIND_ZONED_DATE_TIME) {
                return value;
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_OFFSET_DATE_TIME) {
                return javan_zoned_date_time_new(javan_offset_date_time_checked(value)->epoch_millis);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE_TIME) {
                return javan_local_date_time_at_zone(value, javan_zone_id_system_default());
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_LOCAL_DATE) {
                return javan_local_date_at_start_of_day_zone(value, javan_zone_id_system_default());
            }
            javan_panic("unsupported ZonedDateTime.from temporal");
            return NULL;
        }

        void* javan_zoned_date_time_to_offset_date_time(void* value) {
            return javan_offset_date_time_new(javan_zoned_date_time_checked(value)->epoch_millis);
        }

        void* javan_zoned_date_time_to_local_date(void* value) {
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_zoned_date_time_checked(value)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_date_new(year, month, day);
        }

        void* javan_zoned_date_time_to_local_time(void* value) {
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_zoned_date_time_checked(value)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_time_new(hour, minute, second, millis);
        }

        void* javan_zoned_date_time_to_local_date_time(void* value) {
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_zoned_date_time_checked(value)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_date_time_new(year, month, day, hour, minute, second, millis);
        }

        void* javan_offset_date_time_to_instant(void* value) {
            return javan_instant_new(javan_offset_date_time_checked(value)->epoch_millis);
        }

        int javan_zone_offset_get_total_seconds(void* value) {
            return javan_zone_offset_checked(value)->reserved0;
        }

        void* javan_calendar_get_instance(void) {
            return javan_calendar_new(javan_system_current_time_millis());
        }

        void javan_calendar_set_time(void* value, void* date) {
            javan_calendar_checked(value)->epoch_millis = javan_date_like_epoch_millis(date);
        }

        void javan_calendar_set_time_in_millis(void* value, long long epoch_millis) {
            javan_calendar_checked(value)->epoch_millis = epoch_millis;
        }

        void javan_calendar_set_field(void* value, int field, int field_value) {
            javan_calendar_value* calendar = javan_calendar_checked(value);
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                calendar->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            switch (field) {
                case 1:
                    year = field_value;
                    break;
                case 2:
                    month = field_value + 1;
                    break;
                case 5:
                    day = field_value;
                    break;
                case 11:
                    hour = field_value;
                    break;
                case 12:
                    minute = field_value;
                    break;
                case 13:
                    second = field_value;
                    break;
                case 14:
                    millis = field_value;
                    break;
                default:
                    javan_panic("unsupported calendar field");
            }
            calendar->epoch_millis = javan_epoch_millis_from_local_components(year, month, day, hour, minute, second, millis);
        }

        long long javan_calendar_get_time_in_millis(void* value) {
            return javan_calendar_checked(value)->epoch_millis;
        }

        void* javan_calendar_to_instant(void* value) {
            return javan_instant_new(javan_calendar_checked(value)->epoch_millis);
        }

        static void* javan_logging_level_new(int value, const char* name) {
            javan_logging_level_value* level = (javan_logging_level_value*) javan_alloc(sizeof(javan_logging_level_value));
            level->magic = JAVAN_LOGGING_LEVEL_MAGIC;
            level->value = value;
            level->reserved0 = 0;
            level->reserved1 = 0;
            level->name = name;
            javan_update_runtime_allocation_kind((void*) level, JAVAN_RUNTIME_KIND_LOGGING_LEVEL);
            return (void*) level;
        }

        static void* javan_logging_level_cached(void** root, int value, const char* name) {
            if (*root != NULL) {
                return *root;
            }
            *root = javan_logging_level_new(value, name);
            return *root;
        }

        void* javan_logging_level_off(void) {
            return javan_logging_level_cached(&javan_logging_level_off_root_value, 2147483647, "OFF");
        }

        void* javan_logging_level_severe(void) {
            return javan_logging_level_cached(&javan_logging_level_severe_root_value, 1000, "SEVERE");
        }

        void* javan_logging_level_warning(void) {
            return javan_logging_level_cached(&javan_logging_level_warning_root_value, 900, "WARNING");
        }

        void* javan_logging_level_info(void) {
            return javan_logging_level_cached(&javan_logging_level_info_root_value, 800, "INFO");
        }

        void* javan_logging_level_fine(void) {
            return javan_logging_level_cached(&javan_logging_level_fine_root_value, 500, "FINE");
        }

        void* javan_logging_level_finer(void) {
            return javan_logging_level_cached(&javan_logging_level_finer_root_value, 400, "FINER");
        }

        void* javan_logging_level_all(void) {
            return javan_logging_level_cached(&javan_logging_level_all_root_value, -2147483648, "ALL");
        }

        int javan_logging_level_int_value(void* value) {
            return javan_logging_level_checked(value)->value;
        }

        void* javan_logging_level_to_string(void* value) {
            return javan_string_from(javan_logging_level_checked(value)->name);
        }

        void* javan_simple_date_format_new(void) {
            javan_simple_date_format_value* formatter = (javan_simple_date_format_value*) javan_alloc(sizeof(javan_simple_date_format_value));
            formatter->magic = JAVAN_SIMPLE_DATE_FORMAT_MAGIC;
            formatter->pattern_kind = 0;
            formatter->reserved0 = 0;
            formatter->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) formatter, JAVAN_RUNTIME_KIND_SIMPLE_DATE_FORMAT);
            return (void*) formatter;
        }

        void javan_simple_date_format_init(void* value, void* pattern) {
            javan_simple_date_format_value* formatter = (javan_simple_date_format_value*) value;
            if (formatter == NULL || formatter->magic != JAVAN_SIMPLE_DATE_FORMAT_MAGIC) {
                javan_panic("unsupported simple date format");
            }
            const char* text = javan_charsequence_string_value(pattern);
            if (text == NULL || strcmp(text, "yyyy-MM-dd HH:mm:ss.SSS") != 0) {
                javan_panic("unsupported SimpleDateFormat pattern");
            }
            formatter->pattern_kind = JAVAN_SIMPLE_DATE_FORMAT_PATTERN_NANO_LOG;
        }

        void* javan_simple_date_format_format(void* value, void* date) {
            char buffer[32];
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_simple_date_format_checked(value);
            javan_components_from_epoch_millis_local(
                javan_date_like_epoch_millis(date),
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            snprintf(buffer, sizeof(buffer), "%04d-%02d-%02d %02d:%02d:%02d.%03d", year, month, day, hour, minute, second, millis);
            return javan_string_from(buffer);
        }

        static void javan_uuid_fill_random(unsigned char* buffer, int length) {
            if (buffer == NULL || length <= 0) {
                javan_panic("uuid entropy unavailable");
            }
        #if defined(_WIN32)
            if (SystemFunction036((PVOID) buffer, (ULONG) length) == 0) {
                javan_panic("uuid entropy unavailable");
            }
        #elif defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
            arc4random_buf(buffer, (size_t) length);
        #else
            FILE* random_file = fopen("/dev/urandom", "rb");
            if (random_file == NULL) {
                javan_panic("uuid entropy unavailable");
            }
            size_t bytes_read = fread(buffer, 1, (size_t) length, random_file);
            fclose(random_file);
            if (bytes_read != (size_t) length) {
                javan_panic("uuid entropy unavailable");
            }
        #endif
        }

        void* javan_uuid_random(void) {
            unsigned char bytes[16];
            unsigned long long most = 0;
            unsigned long long least = 0;
            javan_uuid_fill_random(bytes, 16);
            bytes[6] = (unsigned char) ((bytes[6] & 0x0fU) | 0x40U);
            bytes[8] = (unsigned char) ((bytes[8] & 0x3fU) | 0x80U);
            for (int index = 0; index < 8; index++) {
                most = (most << 8) | (unsigned long long) bytes[index];
            }
            for (int index = 8; index < 16; index++) {
                least = (least << 8) | (unsigned long long) bytes[index];
            }
            javan_uuid_value* uuid = (javan_uuid_value*) javan_alloc(sizeof(javan_uuid_value));
            uuid->magic = JAVAN_UUID_MAGIC;
            uuid->reserved0 = 0;
            uuid->reserved1 = 0;
            uuid->reserved2 = 0;
            uuid->most = most;
            uuid->least = least;
            javan_update_runtime_allocation_kind((void*) uuid, JAVAN_RUNTIME_KIND_UUID);
            return (void*) uuid;
        }

        void* javan_uuid_to_string(void* value) {
            char buffer[37];
            javan_uuid_value* uuid = javan_uuid_checked(value);
            unsigned int part1 = (unsigned int) ((uuid->most >> 32) & 0xffffffffULL);
            unsigned int part2 = (unsigned int) ((uuid->most >> 16) & 0xffffULL);
            unsigned int part3 = (unsigned int) (uuid->most & 0xffffULL);
            unsigned int part4 = (unsigned int) ((uuid->least >> 48) & 0xffffULL);
            unsigned long long part5 = uuid->least & 0xffffffffffffULL;
            snprintf(buffer, sizeof(buffer), "%08x-%04x-%04x-%04x-%012llx", part1, part2, part3, part4, part5);
            return javan_string_from(buffer);
        }

        void* javan_date_from_instant(void* instant) {
            return javan_date_new(javan_instant_checked(instant)->epoch_millis);
        }

        void* javan_date_alloc(void) {
            return javan_date_new(0);
        }

        void javan_date_init_now(void* value) {
            javan_date_checked(value)->epoch_millis = javan_system_current_time_millis();
        }

        void javan_date_init_millis(void* value, long long epoch_millis) {
            javan_date_checked(value)->epoch_millis = epoch_millis;
        }

        void* javan_date_to_instant(void* value) {
            return javan_instant_new(javan_date_checked(value)->epoch_millis);
        }

        long long javan_date_get_time(void* value) {
            return javan_date_checked(value)->epoch_millis;
        }

        void* javan_sql_date_alloc(void) {
            return javan_sql_date_new(0);
        }

        void javan_sql_date_init_millis(void* value, long long epoch_millis) {
            javan_sql_date_checked(value)->epoch_millis = epoch_millis;
        }

        void* javan_sql_date_value_of_local_date(void* value) {
            javan_local_date_value* date = javan_local_date_checked(value);
            return javan_sql_date_new(javan_epoch_millis_from_local_components(date->year, date->month, date->day, 0, 0, 0, 0));
        }

        long long javan_sql_date_get_time(void* value) {
            return javan_sql_date_checked(value)->epoch_millis;
        }

        void* javan_sql_date_to_local_date(void* value) {
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_sql_date_checked(value)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_date_new(year, month, day);
        }

        void* javan_sql_time_alloc(void) {
            return javan_sql_time_new(0);
        }

        void javan_sql_time_init_millis(void* value, long long epoch_millis) {
            javan_sql_time_checked(value)->epoch_millis = epoch_millis;
        }

        void* javan_sql_time_value_of_local_time(void* value) {
            javan_local_time_value* time = javan_local_time_checked(value);
            return javan_sql_time_new(javan_epoch_millis_from_local_components(1970, 1, 1, time->hour, time->minute, time->second, 0));
        }

        long long javan_sql_time_get_time(void* value) {
            return javan_sql_time_checked(value)->epoch_millis;
        }

        void* javan_sql_time_to_local_time(void* value) {
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_sql_time_checked(value)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_time_new(hour, minute, second, millis);
        }

        void* javan_sql_timestamp_alloc(void) {
            return javan_sql_timestamp_new(0);
        }

        void javan_sql_timestamp_init_millis(void* value, long long epoch_millis) {
            javan_sql_timestamp_checked(value)->epoch_millis = epoch_millis;
        }

        void* javan_sql_timestamp_from_instant(void* value) {
            return javan_sql_timestamp_new(javan_instant_checked(value)->epoch_millis);
        }

        void* javan_sql_timestamp_value_of_local_date_time(void* value) {
            javan_local_date_time_value* date_time = javan_local_date_time_checked(value);
            return javan_sql_timestamp_new(javan_epoch_millis_from_local_components(
                date_time->year,
                date_time->month,
                date_time->day,
                date_time->hour,
                date_time->minute,
                date_time->second,
                date_time->millis
            ));
        }

        long long javan_sql_timestamp_get_time(void* value) {
            return javan_sql_timestamp_checked(value)->epoch_millis;
        }

        void* javan_sql_timestamp_to_instant(void* value) {
            return javan_instant_new(javan_sql_timestamp_checked(value)->epoch_millis);
        }

        void* javan_sql_timestamp_to_local_date_time(void* value) {
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            javan_components_from_epoch_millis_local(
                javan_sql_timestamp_checked(value)->epoch_millis,
                &year,
                &month,
                &day,
                &hour,
                &minute,
                &second,
                &millis
            );
            return javan_local_date_time_new(year, month, day, hour, minute, second, millis);
        }
        """;

    private static final String SOURCE_HEAP_ALLOC_TAIL_CONT = """

        void* javan_locale_root(void) {
            if (javan_locale_root_value != NULL) {
                return javan_locale_root_value;
            }
            javan_locale_value* locale = (javan_locale_value*) javan_alloc(sizeof(javan_locale_value));
            locale->magic = JAVAN_LOCALE_MAGIC;
            locale->kind = 1;
            locale->reserved0 = 0;
            locale->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) locale, JAVAN_RUNTIME_KIND_LOCALE);
            javan_locale_root_value = (void*) locale;
            return javan_locale_root_value;
        }

        static long long javan_management_start_time_millis(void) {
            javan_runtime_lock_enter();
            if (javan_management_start_time_initialized_value == 0) {
                javan_management_start_time_millis_value = javan_system_current_time_millis();
                javan_management_start_time_initialized_value = 1;
            }
            long long result = javan_management_start_time_millis_value;
            javan_runtime_lock_leave();
            return result;
        }

        static int javan_management_available_processors_native(void) {
        #if defined(_WIN32)
            SYSTEM_INFO info;
            GetSystemInfo(&info);
            if (info.dwNumberOfProcessors == 0) {
                return 1;
            }
            if (info.dwNumberOfProcessors > INT_MAX) {
                return INT_MAX;
            }
            return (int) info.dwNumberOfProcessors;
        #else
            long count = sysconf(_SC_NPROCESSORS_ONLN);
            if (count <= 0) {
                return 1;
            }
            if (count > INT_MAX) {
                return INT_MAX;
            }
            return (int) count;
        #endif
        }

        static double javan_management_system_load_average_native(void) {
        #if defined(_WIN32)
            return -1.0;
        #else
            double load = -1.0;
            if (getloadavg(&load, 1) != 1) {
                return -1.0;
            }
            return load;
        #endif
        }

        static double javan_management_cpu_load_unavailable(void) {
            return -1.0;
        }

        void* javan_runtime_get_runtime(void) {
            if (javan_runtime_root_value != NULL) {
                return javan_runtime_root_value;
            }
            javan_runtime_value* runtime = (javan_runtime_value*) javan_alloc(sizeof(javan_runtime_value));
            runtime->magic = JAVAN_RUNTIME_MAGIC;
            runtime->shutdown_hook_runner_registered = 0;
            runtime->shutdown_hook_running = 0;
            runtime->reserved0 = 0;
            runtime->shutdown_hooks = NULL;
            javan_update_runtime_allocation_kind((void*) runtime, JAVAN_RUNTIME_KIND_RUNTIME);
            javan_runtime_root_value = (void*) runtime;
            return javan_runtime_root_value;
        }

        long long javan_runtime_total_memory(void* value) {
            javan_runtime_checked(value);
            return (long long) javan_heap_live_bytes();
        }

        long long javan_runtime_max_memory(void* value) {
            javan_runtime_checked(value);
            javan_allocation_limit_init();
            if (javan_heap_limit_bytes > 0) {
                return (long long) javan_heap_limit_bytes;
            }
            return (long long) javan_heap_live_bytes();
        }

        long long javan_runtime_free_memory(void* value) {
            long long max = javan_runtime_max_memory(value);
            long long total = javan_runtime_total_memory(value);
            if (max <= total) {
                return 0LL;
            }
            return max - total;
        }

        int javan_runtime_available_processors(void* value) {
            javan_runtime_checked(value);
            return javan_management_available_processors_native();
        }

        static void javan_runtime_run_shutdown_hooks(void) {
            if (javan_runtime_root_value == NULL) {
                return;
            }
            javan_runtime_lock_enter();
            javan_runtime_value* runtime = javan_runtime_checked(javan_runtime_root_value);
            if (runtime->shutdown_hook_running != 0) {
                javan_runtime_lock_leave();
                return;
            }
            runtime->shutdown_hook_running = 1;
            javan_object_list* hooks = runtime->shutdown_hooks;
            javan_runtime_lock_leave();
            if (hooks == NULL) {
                return;
            }
            for (int index = 0; index < hooks->length; index++) {
                void* hook_value = hooks->values[index];
                if (hook_value == NULL) {
                    continue;
                }
                javan_thread* hook = javan_require_thread(hook_value);
                if (hook->started == 0) {
                    javan_thread_start(hook_value);
                }
            }
            for (int index = 0; index < hooks->length; index++) {
                void* hook_value = hooks->values[index];
                if (hook_value == NULL) {
                    continue;
                }
                javan_thread* hook = javan_require_thread(hook_value);
                if (hook != javan_current_thread_object() && javan_thread_has_live_lifecycle(hook) != 0) {
                    javan_thread_join(hook_value);
                }
            }
        }

        void javan_runtime_add_shutdown_hook(void* runtime_value, void* hook_value) {
            void* runtime_root = runtime_value;
            void* hook_root = hook_value;
            void** roots[] = {
                (void**) &runtime_root,
                (void**) &hook_root
            };
            javan_root_frame_push(roots, 2);
            javan_runtime_lock_enter();
            javan_runtime_value* runtime = javan_runtime_checked(runtime_root);
            javan_thread* hook = javan_require_thread(hook_root);
            if (runtime->shutdown_hook_running != 0) {
                javan_runtime_lock_leave();
                javan_root_frame_pop(roots);
                javan_panic("shutdown already in progress");
            }
            if (hook == javan_current_thread_object() || hook->started != 0) {
                javan_runtime_lock_leave();
                javan_root_frame_pop(roots);
                javan_panic("Hook already started");
            }
            if (runtime->shutdown_hooks == NULL) {
                runtime->shutdown_hooks = javan_list_new_with_capacity(4, 0);
            }
            for (int index = 0; index < runtime->shutdown_hooks->length; index++) {
                if (runtime->shutdown_hooks->values[index] == hook_root) {
                    javan_runtime_lock_leave();
                    javan_root_frame_pop(roots);
                    javan_panic("Hook previously registered");
                }
            }
            javan_list_append_raw(runtime->shutdown_hooks, hook_root);
            runtime->shutdown_hooks->mod_count++;
            if (runtime->shutdown_hook_runner_registered == 0) {
                if (atexit(javan_runtime_run_shutdown_hooks) != 0) {
                    javan_runtime_lock_leave();
                    javan_root_frame_pop(roots);
                    javan_panic("unable to register shutdown hook runner");
                }
                runtime->shutdown_hook_runner_registered = 1;
            }
            javan_runtime_lock_leave();
            javan_root_frame_pop(roots);
        }

        int javan_runtime_remove_shutdown_hook(void* runtime_value, void* hook_value) {
            javan_runtime_lock_enter();
            javan_runtime_value* runtime = javan_runtime_checked(runtime_value);
            javan_require_thread(hook_value);
            if (runtime->shutdown_hook_running != 0) {
                javan_runtime_lock_leave();
                javan_panic("shutdown already in progress");
            }
            if (runtime->shutdown_hooks == NULL) {
                javan_runtime_lock_leave();
                return 0;
            }
            int removed = 0;
            for (int index = 0; index < runtime->shutdown_hooks->length; index++) {
                if (runtime->shutdown_hooks->values[index] != hook_value) {
                    continue;
                }
                for (int cursor = index + 1; cursor < runtime->shutdown_hooks->length; cursor++) {
                    runtime->shutdown_hooks->values[cursor - 1] = runtime->shutdown_hooks->values[cursor];
                }
                runtime->shutdown_hooks->length--;
                runtime->shutdown_hooks->values[runtime->shutdown_hooks->length] = NULL;
                runtime->shutdown_hooks->mod_count++;
                removed = 1;
                break;
            }
            javan_runtime_lock_leave();
            return removed;
        }

        void javan_runtime_exit(void* runtime_value, int status) {
            javan_runtime_checked(runtime_value);
            javan_system_exit(status);
        }

        void* javan_management_thread_mxbean(void) {
            if (javan_thread_mxbean_root_value != NULL) {
                return javan_thread_mxbean_root_value;
            }
            javan_thread_mxbean_value* bean = (javan_thread_mxbean_value*) javan_alloc(sizeof(javan_thread_mxbean_value));
            bean->magic = JAVAN_THREAD_MXBEAN_MAGIC;
            bean->reserved0 = 0;
            bean->reserved1 = 0;
            bean->reserved2 = 0;
            javan_update_runtime_allocation_kind((void*) bean, JAVAN_RUNTIME_KIND_THREAD_MXBEAN);
            javan_thread_mxbean_root_value = (void*) bean;
            return javan_thread_mxbean_root_value;
        }

        int javan_thread_mxbean_get_thread_count(void* value) {
            javan_thread_mxbean_checked(value);
            javan_thread_current();
            unsigned long active = javan_heap_active_threads();
            if (active >= (unsigned long) INT_MAX) {
                return INT_MAX;
            }
            return (int) active + 1;
        }

        static long long javan_thread_numeric_id(void* thread_value) {
            return thread_value == NULL ? 0LL : (long long) (uintptr_t) thread_value;
        }

        static void* javan_thread_from_numeric_id(long long thread_id) {
            if (thread_id <= 0LL) {
                return NULL;
            }
            void* candidate = (void*) (uintptr_t) thread_id;
            javan_runtime_lock_enter();
            int present = 0;
            for (int index = 0; index < javan_thread_root_count_value; index++) {
                if (javan_thread_roots_value[index] == candidate) {
                    present = 1;
                    break;
                }
            }
            javan_runtime_lock_leave();
            return present != 0 ? candidate : NULL;
        }

        static void* javan_thread_info_new(long long thread_id, void* thread_name, void* lock_name, void* lock_owner_name) {
            void* thread_name_root = thread_name;
            void* lock_name_root = lock_name;
            void* lock_owner_name_root = lock_owner_name;
            void* info_value = NULL;
            void** roots[] = {
                (void**) &thread_name_root,
                (void**) &lock_name_root,
                (void**) &lock_owner_name_root,
                (void**) &info_value
            };
            javan_root_frame_push(roots, 4);
            info_value = javan_alloc(sizeof(javan_thread_info_value));
            javan_thread_info_value* info = (javan_thread_info_value*) info_value;
            info->magic = JAVAN_THREAD_INFO_MAGIC;
            info->reserved0 = 0;
            info->reserved1 = 0;
            info->reserved2 = 0;
            info->thread_id = thread_id;
            info->thread_name = thread_name_root;
            info->lock_name = lock_name_root;
            info->lock_owner_name = lock_owner_name_root;
            javan_update_runtime_allocation_kind(info_value, JAVAN_RUNTIME_KIND_THREAD_INFO);
            javan_root_frame_pop(roots);
            return info_value;
        }

        void* javan_thread_mxbean_get_all_thread_ids(void* value) {
            javan_thread_mxbean_checked(value);
            javan_thread_current();
            javan_runtime_lock_enter();
            int count = javan_thread_root_count_value;
            javan_runtime_lock_leave();
            void* result = javan_long_array_new(count);
            for (int index = 0; index < count; index++) {
                javan_runtime_lock_enter();
                void* thread_value = index < javan_thread_root_count_value ? javan_thread_roots_value[index] : NULL;
                javan_runtime_lock_leave();
                javan_long_array_set(result, index, javan_thread_numeric_id(thread_value));
            }
            return result;
        }

        void* javan_thread_mxbean_get_thread_info(void* value, void* thread_ids) {
            javan_thread_mxbean_checked(value);
            javan_thread_current();
            void* ids_root = thread_ids;
            void* result = NULL;
            void* info_value = NULL;
            void* thread_name = NULL;
            int length = 0;
            void** roots[] = {
                (void**) &ids_root,
                (void**) &result,
                (void**) &info_value,
                (void**) &thread_name
            };
            javan_root_frame_push(roots, 4);
            length = javan_array_length(ids_root);
            result = javan_object_array_new(length);
            for (int index = 0; index < length; index++) {
                long long thread_id = javan_long_array_get(ids_root, index);
                void* thread_value = javan_thread_from_numeric_id(thread_id);
                if (thread_value == NULL) {
                    javan_object_array_set(result, index, NULL);
                    continue;
                }
                thread_name = javan_thread_get_name(thread_value);
                info_value = javan_thread_info_new(thread_id, thread_name, NULL, NULL);
                javan_object_array_set(result, index, info_value);
                info_value = NULL;
                thread_name = NULL;
            }
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_thread_info_get_thread_name(void* value) {
            return javan_thread_info_checked(value)->thread_name;
        }

        void* javan_thread_info_get_lock_name(void* value) {
            return javan_thread_info_checked(value)->lock_name;
        }

        void* javan_thread_info_get_lock_owner_name(void* value) {
            return javan_thread_info_checked(value)->lock_owner_name;
        }

        void* javan_management_runtime_mxbean(void) {
            if (javan_runtime_mxbean_root_value != NULL) {
                return javan_runtime_mxbean_root_value;
            }
            javan_runtime_mxbean_value* bean = (javan_runtime_mxbean_value*) javan_alloc(sizeof(javan_runtime_mxbean_value));
            bean->magic = JAVAN_RUNTIME_MXBEAN_MAGIC;
            bean->reserved0 = 0;
            bean->reserved1 = 0;
            bean->reserved2 = 0;
            javan_update_runtime_allocation_kind((void*) bean, JAVAN_RUNTIME_KIND_RUNTIME_MXBEAN);
            javan_runtime_mxbean_root_value = (void*) bean;
            return javan_runtime_mxbean_root_value;
        }

        long long javan_runtime_mxbean_get_uptime(void* value) {
            javan_runtime_mxbean_checked(value);
            return javan_system_current_time_millis() - javan_management_start_time_millis();
        }

        long long javan_runtime_mxbean_get_start_time(void* value) {
            javan_runtime_mxbean_checked(value);
            return javan_management_start_time_millis();
        }

        void* javan_management_memory_mxbean(void) {
            if (javan_memory_mxbean_root_value != NULL) {
                return javan_memory_mxbean_root_value;
            }
            javan_memory_mxbean_value* bean = (javan_memory_mxbean_value*) javan_alloc(sizeof(javan_memory_mxbean_value));
            bean->magic = JAVAN_MEMORY_MXBEAN_MAGIC;
            bean->reserved0 = 0;
            bean->reserved1 = 0;
            bean->reserved2 = 0;
            javan_update_runtime_allocation_kind((void*) bean, JAVAN_RUNTIME_KIND_MEMORY_MXBEAN);
            javan_memory_mxbean_root_value = (void*) bean;
            return javan_memory_mxbean_root_value;
        }

        void* javan_memory_mxbean_get_heap_memory_usage(void* value) {
            javan_memory_mxbean_checked(value);
            void* runtime = javan_runtime_get_runtime();
            long long used = javan_runtime_total_memory(runtime);
            long long max = javan_runtime_max_memory(runtime);
            if (max < used) {
                max = used;
            }
            javan_memory_usage_value* usage = (javan_memory_usage_value*) javan_alloc(sizeof(javan_memory_usage_value));
            usage->magic = JAVAN_MEMORY_USAGE_MAGIC;
            usage->reserved0 = 0;
            usage->reserved1 = 0;
            usage->reserved2 = 0;
            usage->used = used;
            usage->committed = used;
            usage->max = max;
            javan_update_runtime_allocation_kind((void*) usage, JAVAN_RUNTIME_KIND_MEMORY_USAGE);
            return (void*) usage;
        }

        long long javan_memory_usage_get_used(void* value) {
            return javan_memory_usage_checked(value)->used;
        }

        long long javan_memory_usage_get_max(void* value) {
            return javan_memory_usage_checked(value)->max;
        }

        void* javan_management_operating_system_mxbean(void) {
            if (javan_operating_system_mxbean_root_value != NULL) {
                return javan_operating_system_mxbean_root_value;
            }
            javan_operating_system_mxbean_value* bean =
                (javan_operating_system_mxbean_value*) javan_alloc(sizeof(javan_operating_system_mxbean_value));
            bean->magic = JAVAN_OPERATING_SYSTEM_MXBEAN_MAGIC;
            bean->reserved0 = 0;
            bean->reserved1 = 0;
            bean->reserved2 = 0;
            javan_update_runtime_allocation_kind((void*) bean, JAVAN_RUNTIME_KIND_OPERATING_SYSTEM_MXBEAN);
            javan_operating_system_mxbean_root_value = (void*) bean;
            return javan_operating_system_mxbean_root_value;
        }

        double javan_operating_system_mxbean_get_system_load_average(void* value) {
            javan_operating_system_mxbean_checked(value);
            return javan_management_system_load_average_native();
        }

        double javan_operating_system_mxbean_get_process_cpu_load(void* value) {
            javan_operating_system_mxbean_checked(value);
            return javan_management_cpu_load_unavailable();
        }

        double javan_operating_system_mxbean_get_cpu_load(void* value) {
            javan_operating_system_mxbean_checked(value);
            return javan_management_cpu_load_unavailable();
        }

        void* javan_process_handle_current(void) {
            if (javan_process_handle_root_value != NULL) {
                return javan_process_handle_root_value;
            }
            javan_process_handle_value* handle = (javan_process_handle_value*) javan_alloc(sizeof(javan_process_handle_value));
            handle->magic = JAVAN_PROCESS_HANDLE_MAGIC;
            handle->reserved0 = 0;
            handle->reserved1 = 0;
            handle->reserved2 = 0;
            #if defined(_WIN32)
            handle->pid = (long long) _getpid();
            #else
            handle->pid = (long long) getpid();
            #endif
            javan_update_runtime_allocation_kind((void*) handle, JAVAN_RUNTIME_KIND_PROCESS_HANDLE);
            javan_process_handle_root_value = (void*) handle;
            return javan_process_handle_root_value;
        }

        long long javan_process_handle_pid(void* value) {
            return javan_process_handle_checked(value)->pid;
        }

        void* javan_datetime_formatter_builder_new(void) {
            void* patterns_value = NULL;
            void** roots[] = {
                (void**) &patterns_value
            };
            javan_root_frame_push(roots, 1);
            patterns_value = javan_list_new_with_capacity(0, 0);
            javan_datetime_formatter_builder_value* builder =
                (javan_datetime_formatter_builder_value*) javan_alloc(sizeof(javan_datetime_formatter_builder_value));
            builder->magic = JAVAN_DATETIME_FORMATTER_BUILDER_MAGIC;
            builder->case_insensitive = 0;
            builder->optional_depth = 0;
            builder->optional_nano_fraction = 0;
            builder->fraction_min_width = 0;
            builder->fraction_max_width = 0;
            builder->fraction_decimal_point = 0;
            builder->reserved = 0;
            builder->patterns = (javan_object_list*) patterns_value;
            javan_update_runtime_allocation_kind((void*) builder, JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER);
            javan_root_frame_pop(roots);
            return (void*) builder;
        }

        static int javan_datetime_formatter_is_nano_of_second(void* field) {
            if (field == NULL) {
                return 0;
            }
            if (javan_registered_type_id(field) < 0) {
                return 0;
            }
            const char* name = (const char*) javan_printable_object_string(field);
            if (name == NULL) {
                return 0;
            }
            return strcmp(name, "NANO_OF_SECOND") == 0;
        }

        void* javan_datetime_formatter_builder_parse_case_insensitive(void* value) {
            javan_datetime_formatter_builder_value* builder = javan_datetime_formatter_builder_checked(value);
            builder->case_insensitive = 1;
            return value;
        }

        void* javan_datetime_formatter_builder_append_pattern(void* value, void* pattern) {
            void* builder_root = value;
            void* pattern_root = pattern;
            void** roots[] = {
                (void**) &builder_root,
                (void**) &pattern_root
            };
            javan_root_frame_push(roots, 2);
            javan_datetime_formatter_builder_value* builder =
                javan_datetime_formatter_builder_checked(builder_root);
            if (pattern_root == NULL) {
                javan_root_frame_pop(roots);
                javan_panic("DateTimeFormatterBuilder.appendPattern null");
            }
            javan_list_append_raw(builder->patterns, pattern_root);
            javan_root_frame_pop(roots);
            return builder_root;
        }

        void* javan_datetime_formatter_builder_optional_start(void* value) {
            javan_datetime_formatter_builder_value* builder = javan_datetime_formatter_builder_checked(value);
            builder->optional_depth++;
            return value;
        }

        void* javan_datetime_formatter_builder_append_fraction(
            void* value,
            void* field,
            int min_width,
            int max_width,
            int decimal_point
        ) {
            javan_datetime_formatter_builder_value* builder = javan_datetime_formatter_builder_checked(value);
            if (javan_datetime_formatter_is_nano_of_second(field) == 0) {
                javan_panic("unsupported TemporalField for appendFraction");
            }
            if (min_width < 0 || max_width < min_width) {
                javan_panic("invalid appendFraction width");
            }
            builder->optional_nano_fraction = 1;
            builder->fraction_min_width = min_width;
            builder->fraction_max_width = max_width;
            builder->fraction_decimal_point = decimal_point == 0 ? 0 : 1;
            return value;
        }

        void* javan_datetime_formatter_builder_optional_end(void* value) {
            javan_datetime_formatter_builder_value* builder = javan_datetime_formatter_builder_checked(value);
            if (builder->optional_depth <= 0) {
                javan_panic("DateTimeFormatterBuilder.optionalEnd without optionalStart");
            }
            builder->optional_depth--;
            return value;
        }

        static javan_object_list* javan_datetime_formatter_copy_patterns(javan_object_list* source) {
            if (source == NULL || source->magic != JAVAN_OBJECT_LIST_MAGIC) {
                javan_panic("invalid datetime formatter pattern list");
            }
            javan_object_list* copy = javan_list_new_with_capacity(source->length, 0);
            for (int index = 0; index < source->length; index++) {
                javan_list_append_raw(copy, source->values[index]);
            }
            return copy;
        }

        void* javan_datetime_formatter_builder_to_formatter(void* value, void* locale) {
            void* builder_root = value;
            void* locale_root = locale;
            void* patterns_root = NULL;
            void** roots[] = {
                (void**) &builder_root,
                (void**) &locale_root,
                (void**) &patterns_root
            };
            javan_root_frame_push(roots, 3);
            javan_datetime_formatter_builder_value* builder =
                javan_datetime_formatter_builder_checked(builder_root);
            javan_locale_checked(locale_root);
            if (builder->optional_depth != 0) {
                javan_root_frame_pop(roots);
                javan_panic("DateTimeFormatterBuilder.toFormatter with unclosed optional section");
            }
            patterns_root = (void*) javan_datetime_formatter_copy_patterns(builder->patterns);
            javan_datetime_formatter_value* formatter =
                (javan_datetime_formatter_value*) javan_alloc(sizeof(javan_datetime_formatter_value));
            formatter->magic = JAVAN_DATETIME_FORMATTER_MAGIC;
            formatter->case_insensitive = builder->case_insensitive;
            formatter->optional_nano_fraction = builder->optional_nano_fraction;
            formatter->fraction_min_width = builder->fraction_min_width;
            formatter->fraction_max_width = builder->fraction_max_width;
            formatter->fraction_decimal_point = builder->fraction_decimal_point;
            formatter->reserved0 = 0;
            formatter->reserved1 = 0;
            formatter->patterns = (javan_object_list*) patterns_root;
            formatter->locale = locale_root;
            javan_update_runtime_allocation_kind((void*) formatter, JAVAN_RUNTIME_KIND_DATETIME_FORMATTER);
            javan_root_frame_pop(roots);
            return (void*) formatter;
        }

        static int javan_ascii_parse_digits(const char* text, int begin, int count, int* result) {
            int value = 0;
            for (int index = 0; index < count; index++) {
                unsigned char ch = (unsigned char) text[begin + index];
                if (!javan_ascii_is_digit(ch)) {
                    return 0;
                }
                value = (value * 10) + (int) (ch - '0');
            }
            *result = value;
            return 1;
        }

        static int javan_is_leap_year(int year) {
            return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
        }

        static int javan_days_in_month(int year, int month) {
            switch (month) {
                case 1:
                case 3:
                case 5:
                case 7:
                case 8:
                case 10:
                case 12:
                    return 31;
                case 4:
                case 6:
                case 9:
                case 11:
                    return 30;
                case 2:
                    return javan_is_leap_year(year) ? 29 : 28;
                default:
                    return 0;
            }
        }

        static int javan_validate_local_date_components(int year, int month, int day) {
            if (year < 1 || month < 1 || month > 12) {
                return 0;
            }
            int days_in_month = javan_days_in_month(year, month);
            return day >= 1 && day <= days_in_month;
        }

        static int javan_validate_local_time_components(int hour, int minute, int second, int millis) {
            return hour >= 0 && hour <= 23
                && minute >= 0 && minute <= 59
                && second >= 0 && second <= 59
                && millis >= 0 && millis <= 999;
        }

        static int javan_datetime_formatter_parse_fraction(
            const char* text,
            int begin,
            int text_length,
            int min_width,
            int max_width,
            int decimal_point,
            int* millis,
            int* consumed
        ) {
            int index = begin;
            if (decimal_point != 0) {
                if (index >= text_length || text[index] != '.') {
                    return min_width == 0;
                }
                index++;
            }
            int digits = 0;
            int value = 0;
            while (index < text_length && digits < max_width) {
                unsigned char ch = (unsigned char) text[index];
                if (!javan_ascii_is_digit(ch)) {
                    break;
                }
                if (digits < 3) {
                    value = (value * 10) + (int) (ch - '0');
                }
                digits++;
                index++;
            }
            if (digits < min_width) {
                return 0;
            }
            if (digits == 1) {
                value *= 100;
            } else if (digits == 2) {
                value *= 10;
            }
            while (index < text_length && javan_ascii_is_digit((unsigned char) text[index])) {
                return 0;
            }
            *millis = value;
            *consumed = index - begin;
            return 1;
        }

        static void* javan_datetime_formatter_parse_local_date(const char* text) {
            int length = javan_string_length(text);
            int year = 0;
            int month = 0;
            int day = 0;
            if (length != 10
                || text[4] != '-'
                || text[7] != '-'
                || !javan_ascii_parse_digits(text, 0, 4, &year)
                || !javan_ascii_parse_digits(text, 5, 2, &month)
                || !javan_ascii_parse_digits(text, 8, 2, &day)
                || !javan_validate_local_date_components(year, month, day)) {
                javan_panic("unsupported datetime formatter parse text");
            }
            return javan_local_date_new(year, month, day);
        }

        static void* javan_datetime_formatter_parse_local_time(
            const char* text,
            int optional_nano_fraction,
            int fraction_min_width,
            int fraction_max_width,
            int fraction_decimal_point
        ) {
            int text_length = javan_string_length(text);
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            int consumed = 0;
            if (text_length < 8
                || text[2] != ':'
                || text[5] != ':'
                || !javan_ascii_parse_digits(text, 0, 2, &hour)
                || !javan_ascii_parse_digits(text, 3, 2, &minute)
                || !javan_ascii_parse_digits(text, 6, 2, &second)) {
                javan_panic("unsupported datetime formatter parse text");
            }
            if (optional_nano_fraction != 0
                && !javan_datetime_formatter_parse_fraction(
                    text,
                    8,
                    text_length,
                    fraction_min_width,
                    fraction_max_width,
                    fraction_decimal_point,
                    &millis,
                    &consumed
                )) {
                javan_panic("unsupported datetime formatter parse text");
            }
            if (!javan_validate_local_time_components(hour, minute, second, millis)
                || 8 + consumed != text_length) {
                javan_panic("unsupported datetime formatter parse text");
            }
            return javan_local_time_new(hour, minute, second, millis);
        }

        static void* javan_datetime_formatter_parse_local_date_time(
            const char* text,
            char separator,
            int optional_nano_fraction,
            int fraction_min_width,
            int fraction_max_width,
            int fraction_decimal_point
        ) {
            int text_length = javan_string_length(text);
            int year = 0;
            int month = 0;
            int day = 0;
            int hour = 0;
            int minute = 0;
            int second = 0;
            int millis = 0;
            int consumed = 0;
            if (text_length < 19
                || text[4] != '-'
                || text[7] != '-'
                || text[10] != separator
                || text[13] != ':'
                || text[16] != ':'
                || !javan_ascii_parse_digits(text, 0, 4, &year)
                || !javan_ascii_parse_digits(text, 5, 2, &month)
                || !javan_ascii_parse_digits(text, 8, 2, &day)
                || !javan_ascii_parse_digits(text, 11, 2, &hour)
                || !javan_ascii_parse_digits(text, 14, 2, &minute)
                || !javan_ascii_parse_digits(text, 17, 2, &second)) {
                javan_panic("unsupported datetime formatter parse text");
            }
            if (optional_nano_fraction != 0
                && !javan_datetime_formatter_parse_fraction(
                    text,
                    19,
                    text_length,
                    fraction_min_width,
                    fraction_max_width,
                    fraction_decimal_point,
                    &millis,
                    &consumed
                )) {
                javan_panic("unsupported datetime formatter parse text");
            }
            if (!javan_validate_local_date_components(year, month, day)
                || !javan_validate_local_time_components(hour, minute, second, millis)
                || 19 + consumed != text_length) {
                javan_panic("unsupported datetime formatter parse text");
            }
            return javan_local_date_time_new(year, month, day, hour, minute, second, millis);
        }

        void* javan_datetime_formatter_parse(void* value, void* text) {
            javan_datetime_formatter_value* formatter = javan_datetime_formatter_checked(value);
            const char* source = javan_charsequence_string_value(text);
            if (formatter->patterns->length != 1) {
                javan_panic("unsupported datetime formatter parse shape");
            }
            const char* pattern = javan_charsequence_string_value(formatter->patterns->values[0]);
            if (javan_string_equals(pattern, "yyyy-MM-dd")) {
                return javan_datetime_formatter_parse_local_date(source);
            }
            if (javan_string_equals(pattern, "HH:mm:ss")) {
                return javan_datetime_formatter_parse_local_time(
                    source,
                    formatter->optional_nano_fraction,
                    formatter->fraction_min_width,
                    formatter->fraction_max_width,
                    formatter->fraction_decimal_point
                );
            }
            if (javan_string_equals(pattern, "yyyy-MM-dd'T'HH:mm:ss")) {
                return javan_datetime_formatter_parse_local_date_time(
                    source,
                    'T',
                    formatter->optional_nano_fraction,
                    formatter->fraction_min_width,
                    formatter->fraction_max_width,
                    formatter->fraction_decimal_point
                );
            }
            if (javan_string_equals(pattern, "yyyy-MM-dd HH:mm:ss")) {
                return javan_datetime_formatter_parse_local_date_time(
                    source,
                    ' ',
                    formatter->optional_nano_fraction,
                    formatter->fraction_min_width,
                    formatter->fraction_max_width,
                    formatter->fraction_decimal_point
                );
            }
            javan_panic("unsupported datetime formatter parse shape");
            return NULL;
        }

        """;

    private static final String SOURCE_HEAP_ALLOC_TAIL_CONT_THREADS = """

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

        static void javan_http_server_wait_for_completion(javan_http_server_value* server) {
            if (server == NULL) {
                javan_panic("invalid HttpServer completion state");
            }
            #if defined(_WIN32)
            if (server->native_handle != NULL) {
                if (WaitForSingleObject((HANDLE) server->native_handle, INFINITE) != WAIT_OBJECT_0) {
                    javan_panic("HttpServer.start host wait failed");
                }
                CloseHandle((HANDLE) server->native_handle);
                server->native_handle = NULL;
            }
            #else
            if (server->native_handle != NULL) {
                pthread_t* handle = (pthread_t*) server->native_handle;
                if (pthread_join(*handle, NULL) != 0) {
                    javan_panic("HttpServer.start host wait failed");
                }
                free(handle);
                server->native_handle = NULL;
            }
            #endif
            server->completed = 1;
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
            if (thread->target == NULL) {
                javan_thread_leave_live_root(value);
                javan_thread_completion_signal(thread);
                return;
            }
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
                javan_runtime_lock_enter();
                int done = thread->started == 0 || thread->native_completion_signaled != 0;
                javan_runtime_lock_leave();
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
                int next_is_thread = 0;
                javan_runtime_lock_enter();
                javan_thread* current = javan_current_thread_object();
                for (int index = 0; index < javan_thread_root_count_value; index++) {
                    void* candidate = javan_thread_roots_value[index];
                    if (candidate == NULL || candidate == (void*) current) {
                        continue;
                    }
                    if (javan_registered_type_id(candidate) != JAVAN_TYPE_JAVA_LANG_THREAD) {
                        continue;
                    }
                    javan_thread* thread = (javan_thread*) candidate;
                    if (thread->started != 0 && thread->completed == 0) {
                        next = candidate;
                        next_is_thread = 1;
                        break;
                    }
                }
                if (next == NULL) {
                    javan_allocation_node* node = javan_allocations;
                    while (node != NULL) {
                        if (node->kind == JAVAN_HEAP_KIND_RUNTIME && node->runtime_kind == JAVAN_RUNTIME_KIND_HTTP_SERVER) {
                            javan_http_server_value* server = (javan_http_server_value*) node->value;
                            if (server != NULL && server->magic == JAVAN_HTTP_SERVER_MAGIC && server->started != 0 && server->completed == 0) {
                                next = server;
                                next_is_thread = 0;
                                break;
                            }
                        }
                        node = node->next;
                    }
                }
                javan_runtime_lock_leave();
                if (next == NULL) {
                    return;
                }
                if (next_is_thread != 0) {
                    javan_thread_join(next);
                } else {
                    javan_http_server_wait_for_completion((javan_http_server_value*) next);
                }
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

        static int javan_type_descriptor_contains_assignable_name(
            JavanTypeDescriptor* descriptor,
            const char* binary_name
        ) {
            if (descriptor == NULL || binary_name == NULL) {
                return 0;
            }
            for (int index = 0; index < descriptor->assignable_name_count; index++) {
                const char* candidate = descriptor->assignable_names[index];
                if (candidate != NULL && strcmp(candidate, binary_name) == 0) {
                    return 1;
                }
            }
            return 0;
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
            if (list->backing != NULL) {
                return;
            }
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
            if (runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST
                || runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET) {
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_OPTIONAL_INT) {
                javan_optional_int* optional = (javan_optional_int*) value;
                if (optional != NULL && optional->magic != JAVAN_OPTIONAL_INT_MAGIC) {
                    javan_panic("invalid optional int runtime object");
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_FUTURE) {
                javan_future_value* state = (javan_future_value*) value;
                if (state != NULL && state->magic == JAVAN_FUTURE_MAGIC) {
                    javan_gc_mark_value(state->thread);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_THREAD_INFO) {
                javan_thread_info_value* state = (javan_thread_info_value*) value;
                if (state != NULL && state->magic == JAVAN_THREAD_INFO_MAGIC) {
                    javan_gc_mark_value(state->thread_name);
                    javan_gc_mark_value(state->lock_name);
                    javan_gc_mark_value(state->lock_owner_name);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_RUNTIME) {
                javan_runtime_value* runtime = (javan_runtime_value*) value;
                if (runtime != NULL && runtime->magic == JAVAN_RUNTIME_MAGIC) {
                    javan_gc_mark_value((void*) runtime->shutdown_hooks);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_ATOMIC_REFERENCE) {
                javan_atomic_reference* state = (javan_atomic_reference*) value;
                if (state != NULL && state->magic == JAVAN_ATOMIC_REFERENCE_MAGIC) {
                    javan_gc_mark_value(state->value);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_THROWABLE) {
                javan_throwable_value* throwable = (javan_throwable_value*) value;
                if (throwable != NULL && throwable->magic == JAVAN_THROWABLE_MAGIC) {
                    javan_gc_mark_value(throwable->message);
                    javan_gc_mark_value((void*) throwable->suppressed);
                    javan_gc_mark_value(throwable->stack_trace);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER_BUILDER) {
                javan_datetime_formatter_builder_value* builder = (javan_datetime_formatter_builder_value*) value;
                if (builder != NULL && builder->magic == JAVAN_DATETIME_FORMATTER_BUILDER_MAGIC) {
                    javan_gc_mark_value((void*) builder->patterns);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_DATETIME_FORMATTER) {
                javan_datetime_formatter_value* formatter = (javan_datetime_formatter_value*) value;
                if (formatter != NULL && formatter->magic == JAVAN_DATETIME_FORMATTER_MAGIC) {
                    javan_gc_mark_value((void*) formatter->patterns);
                    javan_gc_mark_value(formatter->locale);
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
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_EXCHANGE) {
                javan_http_exchange_value* exchange = (javan_http_exchange_value*) value;
                if (exchange != NULL && exchange->magic == JAVAN_HTTP_EXCHANGE_MAGIC) {
                    javan_gc_mark_value((void*) exchange->request_uri);
                    javan_gc_mark_value((void*) exchange->request_headers);
                    javan_gc_mark_value(exchange->request_body);
                    javan_gc_mark_value((void*) exchange->response_headers);
                    javan_gc_mark_value(exchange->response_body);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_INPUT_STREAM) {
                javan_http_input_stream_value* stream = (javan_http_input_stream_value*) value;
                if (stream != NULL && stream->magic == JAVAN_HTTP_INPUT_STREAM_MAGIC) {
                    javan_gc_mark_value(stream->bytes);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_OUTPUT_STREAM) {
                javan_http_output_stream_value* stream = (javan_http_output_stream_value*) value;
                if (stream != NULL && stream->magic == JAVAN_HTTP_OUTPUT_STREAM_MAGIC) {
                    javan_gc_mark_value((void*) stream->exchange);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_SERVER) {
                javan_http_server_value* server = (javan_http_server_value*) value;
                if (server != NULL && server->magic == JAVAN_HTTP_SERVER_MAGIC) {
                    javan_gc_mark_value((void*) server->address);
                    javan_gc_mark_value((void*) server->server_socket);
                    javan_gc_mark_value((void*) server->contexts);
                    javan_gc_mark_value(server->executor);
                }
            } else if (runtime_kind == JAVAN_RUNTIME_KIND_HTTP_CONTEXT) {
                javan_http_context_value* context = (javan_http_context_value*) value;
                if (context != NULL && context->magic == JAVAN_HTTP_CONTEXT_MAGIC) {
                    javan_gc_mark_value((void*) context->server);
                    javan_gc_mark_value((void*) context->path);
                    javan_gc_mark_value(context->handler);
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
            javan_gc_mark_value(javan_locale_root_value);
            javan_gc_mark_value(javan_runtime_root_value);
            javan_gc_mark_value(javan_thread_mxbean_root_value);
            javan_gc_mark_value(javan_runtime_mxbean_root_value);
            javan_gc_mark_value(javan_memory_mxbean_root_value);
            javan_gc_mark_value(javan_operating_system_mxbean_root_value);
            javan_gc_mark_value(javan_process_handle_root_value);
            javan_gc_mark_value(javan_logging_level_off_root_value);
            javan_gc_mark_value(javan_logging_level_severe_root_value);
            javan_gc_mark_value(javan_logging_level_warning_root_value);
            javan_gc_mark_value(javan_logging_level_info_root_value);
            javan_gc_mark_value(javan_logging_level_fine_root_value);
            javan_gc_mark_value(javan_logging_level_finer_root_value);
            javan_gc_mark_value(javan_logging_level_all_root_value);
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

        void* javan_arrays_copy_of_boolean(void* array, int new_length) {
            return javan_arrays_copy_of(array, new_length, JAVAN_ARRAY_KIND_BOOLEAN, javan_boolean_array_new);
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

        void* javan_string_get_bytes_charset(void* value, void* charset) {
            if (value == NULL) {
                javan_panic("null string");
            }
            if (charset == NULL || strcmp((const char*) charset, "UTF-8") != 0) {
                javan_panic("unsupported charset");
            }
            void** javan_string_get_bytes_roots[] = {
                (void**) &value,
                (void**) &charset
            };
            javan_root_frame_push(javan_string_get_bytes_roots, 2);
            const char* text = (const char*) value;
            void* result = javan_byte_array_from((const signed char*) text, (int) strlen(text));
            javan_root_frame_pop(javan_string_get_bytes_roots);
            return result;
        }

        void* javan_string_from_utf8_bytes(void* array, int offset, int count, void* charset) {
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(array);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            if (charset == NULL || strcmp((const char*) charset, "UTF-8") != 0) {
                javan_panic("unsupported charset");
            }
            if (offset < 0 || count < 0 || offset > bytes->length || count > bytes->length - offset) {
                javan_panic("string index out of bounds");
            }
            void** javan_string_bytes_roots[] = {
                (void**) &bytes,
                (void**) &charset
            };
            javan_root_frame_push(javan_string_bytes_roots, 2);
            char* result = javan_string_alloc((unsigned long) count + 1UL);
            if (count > 0) {
                memcpy(result, bytes->values + offset, (unsigned long) count);
            }
            result[count] = '\\0';
            javan_root_frame_pop(javan_string_bytes_roots);
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

        int javan_character_is_whitespace(int value) {
            unsigned int ch = (unsigned int) (value & 0xff);
            return ch == ' '
                || ch == '\\t'
                || ch == '\\n'
                || ch == '\\r'
                || ch == '\\f'
                || ch == 0x0b;
        }

        static const char* javan_charsequence_string_value(void* value) {
            if (value == NULL) {
                javan_panic("unsupported CharSequence");
            }
            javan_runtime_lock_enter();
            javan_allocation_node* node = javan_find_allocation(value, NULL);
            if (node == NULL) {
                javan_runtime_lock_leave();
                return (const char*) value;
            }
            if (node->kind == JAVAN_HEAP_KIND_RUNTIME && node->runtime_kind == JAVAN_RUNTIME_KIND_STRING) {
                javan_runtime_lock_leave();
                return (const char*) value;
            }
            javan_runtime_lock_leave();
            javan_panic("unsupported CharSequence");
            return NULL;
        }

        int javan_charsequence_length(void* value) {
            int runtime_kind = javan_runtime_kind_of(value);
            if (runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_length(value);
            }
            return javan_string_length(javan_charsequence_string_value(value));
        }

        int javan_charsequence_char_at(void* value, int index) {
            int runtime_kind = javan_runtime_kind_of(value);
            if (runtime_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                return javan_stringbuilder_char_at(value, index);
            }
            return javan_string_char_at(javan_charsequence_string_value(value), index);
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

        static unsigned char javan_ascii_to_lower(unsigned char value) {
            if (value >= 'A' && value <= 'Z') {
                return (unsigned char) (value + ('a' - 'A'));
            }
            return value;
        }

        int javan_string_equals_ignore_case(const char* left, const char* right) {
            if (left == NULL) {
                javan_panic("null string");
            }
            if (right == NULL) {
                return 0;
            }
            unsigned long left_length = strlen(left);
            unsigned long right_length = strlen(right);
            if (left_length != right_length) {
                return 0;
            }
            for (unsigned long index = 0; index < left_length; index++) {
                if (javan_ascii_to_lower((unsigned char) left[index]) != javan_ascii_to_lower((unsigned char) right[index])) {
                    return 0;
                }
            }
            return 1;
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

        void* javan_string_replace_sequence(const char* value, void* old_value, void* new_value) {
            if (value == NULL || old_value == NULL || new_value == NULL) {
                javan_panic("null string");
            }
            void* source_root = (void*) value;
            void* old_root = old_value;
            void* new_root = new_value;
            void** javan_string_replace_sequence_roots[] = {
                (void**) &source_root,
                (void**) &old_root,
                (void**) &new_root
            };
            javan_root_frame_push(javan_string_replace_sequence_roots, 3);
            int old_kind = javan_runtime_kind_of(old_root);
            if (old_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                old_root = javan_stringbuilder_to_string(old_root);
            } else {
                old_root = (void*) javan_charsequence_string_value(old_root);
            }
            int new_kind = javan_runtime_kind_of(new_root);
            if (new_kind == JAVAN_RUNTIME_KIND_STRING_BUILDER) {
                new_root = javan_stringbuilder_to_string(new_root);
            } else {
                new_root = (void*) javan_charsequence_string_value(new_root);
            }
            const char* source = (const char*) source_root;
            const char* old_string = (const char*) old_root;
            const char* new_string = (const char*) new_root;
            size_t source_length = strlen(source);
            size_t old_length = strlen(old_string);
            size_t new_length = strlen(new_string);
            if (old_length == 0) {
                if (new_length != 0 && source_length > (SIZE_MAX - new_length) / (new_length + 1)) {
                    javan_root_frame_pop(javan_string_replace_sequence_roots);
                    javan_panic("string length overflow");
                }
                size_t result_length = source_length + ((source_length + 1) * new_length);
                if (result_length > INT_MAX) {
                    javan_root_frame_pop(javan_string_replace_sequence_roots);
                    javan_panic("string length overflow");
                }
                char* result = javan_string_alloc(result_length + 1);
                char* cursor = result;
                memcpy(cursor, new_string, new_length);
                cursor += new_length;
                for (size_t index = 0; index < source_length; index++) {
                    *cursor++ = source[index];
                    memcpy(cursor, new_string, new_length);
                    cursor += new_length;
                }
                *cursor = '\\0';
                javan_root_frame_pop(javan_string_replace_sequence_roots);
                return result;
            }
            size_t match_count = 0;
            const char* scan = source;
            const char* match = strstr(scan, old_string);
            while (match != NULL) {
                match_count++;
                scan = match + old_length;
                match = strstr(scan, old_string);
            }
            if (match_count == 0) {
                void* result = source_root;
                javan_root_frame_pop(javan_string_replace_sequence_roots);
                return result;
            }
            size_t result_length = source_length;
            if (new_length >= old_length) {
                size_t delta = new_length - old_length;
                if (delta != 0 && match_count > (SIZE_MAX - result_length) / delta) {
                    javan_root_frame_pop(javan_string_replace_sequence_roots);
                    javan_panic("string length overflow");
                }
                result_length += match_count * delta;
            } else {
                result_length -= match_count * (old_length - new_length);
            }
            if (result_length > INT_MAX) {
                javan_root_frame_pop(javan_string_replace_sequence_roots);
                javan_panic("string length overflow");
            }
            char* result = javan_string_alloc(result_length + 1);
            char* cursor = result;
            const char* chunk = source;
            match = strstr(chunk, old_string);
            while (match != NULL) {
                size_t chunk_length = (size_t) (match - chunk);
                memcpy(cursor, chunk, chunk_length);
                cursor += chunk_length;
                memcpy(cursor, new_string, new_length);
                cursor += new_length;
                chunk = match + old_length;
                match = strstr(chunk, old_string);
            }
            size_t tail_length = strlen(chunk);
            memcpy(cursor, chunk, tail_length);
            cursor += tail_length;
            *cursor = '\\0';
            javan_root_frame_pop(javan_string_replace_sequence_roots);
            return result;
        }

        static int javan_ascii_is_digit(unsigned char value) {
            return value >= '0' && value <= '9';
        }

        static int javan_ascii_is_alpha(unsigned char value) {
            unsigned char lower = javan_ascii_to_lower(value);
            return lower >= 'a' && lower <= 'z';
        }

        void* javan_string_replace_all_whitespace(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            void* source_root = (void*) value;
            void** roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(roots, 1);
            char* result = javan_string_alloc(length + 1);
            unsigned long output = 0;
            int changed = 0;
            for (unsigned long index = 0; index < length; index++) {
                unsigned char ch = (unsigned char) ((const char*) source_root)[index];
                if (javan_character_is_whitespace(ch)) {
                    changed = 1;
                    continue;
                }
                result[output++] = (char) ch;
            }
            result[output] = '\\0';
            if (!changed) {
                javan_root_frame_pop(roots);
                return source_root;
            }
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_string_replace_all_non_digits(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            void* source_root = (void*) value;
            void** roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(roots, 1);
            char* result = javan_string_alloc(length + 1);
            unsigned long output = 0;
            int changed = 0;
            for (unsigned long index = 0; index < length; index++) {
                unsigned char ch = (unsigned char) ((const char*) source_root)[index];
                if (!javan_ascii_is_digit(ch)) {
                    changed = 1;
                    continue;
                }
                result[output++] = (char) ch;
            }
            result[output] = '\\0';
            if (!changed) {
                javan_root_frame_pop(roots);
                return source_root;
            }
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_string_replace_all_non_alnum_dot_with_dot(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            void* source_root = (void*) value;
            void** roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(roots, 1);
            char* result = javan_string_alloc(length + 1);
            int changed = 0;
            for (unsigned long index = 0; index < length; index++) {
                unsigned char ch = (unsigned char) ((const char*) source_root)[index];
                if (javan_ascii_is_alpha(ch) || javan_ascii_is_digit(ch) || ch == '.') {
                    result[index] = (char) ch;
                    continue;
                }
                result[index] = '.';
                changed = 1;
            }
            result[length] = '\\0';
            if (!changed) {
                javan_root_frame_pop(roots);
                return source_root;
            }
            javan_root_frame_pop(roots);
            return result;
        }

        void* javan_string_trim_dot_edges(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            unsigned long start = 0;
            while (start < length && value[start] == '.') {
                start++;
            }
            unsigned long end = length;
            while (end > start && value[end - 1] == '.') {
                end--;
            }
            if (start == 0 && end == length) {
                return (void*) value;
            }
            unsigned long result_length = end - start;
            char* result = javan_string_alloc(result_length + 1);
            if (result_length > 0) {
                memcpy(result, value + start, result_length);
            }
            result[result_length] = '\\0';
            return result;
        }

        void* javan_string_remove_pem_markers(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            void* source_root = (void*) value;
            void** roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(roots, 1);
            char* result = javan_string_alloc(length + 1);
            unsigned long output = 0;
            unsigned long index = 0;
            int changed = 0;
            while (index < length) {
                if (index + 5 <= length
                    && strncmp(((const char*) source_root) + index, "-----", 5) == 0) {
                    unsigned long close = index + 5;
                    while (close + 5 <= length) {
                        if (strncmp(((const char*) source_root) + close, "-----", 5) == 0) {
                            index = close + 5;
                            changed = 1;
                            break;
                        }
                        close++;
                    }
                    if (changed && index == close + 5) {
                        continue;
                    }
                }
                result[output++] = ((const char*) source_root)[index];
                index++;
            }
            result[output] = '\\0';
            if (!changed) {
                javan_root_frame_pop(roots);
                return source_root;
            }
            javan_root_frame_pop(roots);
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

        int javan_string_is_blank(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = javan_string_length(value);
            for (int index = 0; index < length; index++) {
                if (javan_character_is_whitespace((unsigned char) value[index]) == 0) {
                    return 0;
                }
            }
            return 1;
        }

        void* javan_string_to_lower_case(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            unsigned long length = strlen(value);
            void* source_root = (void*) value;
            void** javan_string_lower_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_lower_roots, 1);
            char* result = javan_string_alloc(length + 1);
            for (unsigned long index = 0; index < length; index++) {
                result[index] = (char) javan_ascii_to_lower((unsigned char) ((const char*) source_root)[index]);
            }
            result[length] = '\\0';
            javan_root_frame_pop(javan_string_lower_roots);
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

        void* javan_string_split_literal_char(const char* value, int delimiter) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = javan_string_length(value);
            int count = 1;
            for (int index = 0; index < length; index++) {
                if ((unsigned char) value[index] == (unsigned char) delimiter) {
                    count++;
                }
            }
            while (count > 0 && length > 0 && (unsigned char) value[length - 1] == (unsigned char) delimiter) {
                count--;
                length--;
            }
            javan_object_array* result = NULL;
            void** javan_split_literal_roots[] = {
                (void**) &result
            };
            javan_root_frame_push(javan_split_literal_roots, 1);
            result = (javan_object_array*) javan_object_array_new(count);
            int token_start = 0;
            int token_index = 0;
            for (int index = 0; index <= length; index++) {
                if (index == length || (unsigned char) value[index] == (unsigned char) delimiter) {
                    result->values[token_index++] = javan_string_substring_range(value, token_start, index);
                    token_start = index + 1;
                }
            }
            javan_root_frame_pop(javan_split_literal_roots);
            return result;
        }

        void* javan_string_split_whitespace_keep_all(const char* value) {
            if (value == NULL) {
                javan_panic("null string");
            }
            int length = javan_string_length(value);
            int count = 0;
            int token_start = 0;
            while (1) {
                int delimiter_start = token_start;
                while (delimiter_start < length && !javan_character_is_whitespace((unsigned char) value[delimiter_start])) {
                    delimiter_start++;
                }
                count++;
                if (delimiter_start >= length) {
                    break;
                }
                int next_start = delimiter_start;
                while (next_start < length && javan_character_is_whitespace((unsigned char) value[next_start])) {
                    next_start++;
                }
                if (next_start >= length) {
                    count++;
                    break;
                }
                token_start = next_start;
            }
            javan_object_array* result = NULL;
            void** javan_split_whitespace_roots[] = {
                (void**) &result
            };
            javan_root_frame_push(javan_split_whitespace_roots, 1);
            result = (javan_object_array*) javan_object_array_new(count);
            int token_index = 0;
            token_start = 0;
            while (1) {
                int delimiter_start = token_start;
                while (delimiter_start < length && !javan_character_is_whitespace((unsigned char) value[delimiter_start])) {
                    delimiter_start++;
                }
                result->values[token_index++] = javan_string_substring_range(value, token_start, delimiter_start);
                if (delimiter_start >= length) {
                    break;
                }
                int next_start = delimiter_start;
                while (next_start < length && javan_character_is_whitespace((unsigned char) value[next_start])) {
                    next_start++;
                }
                if (next_start >= length) {
                    result->values[token_index++] = javan_string_substring_range(value, length, length);
                    break;
                }
                token_start = next_start;
            }
            javan_root_frame_pop(javan_split_whitespace_roots);
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
            if (left_type == right_type && left_type == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                return ((javan_boxed_character*) left)->value == ((javan_boxed_character*) right)->value;
            }
            if (left_type != 0 || right_type != 0) {
                return 0;
            }
            if (javan_probably_string_key(left) != 0 && javan_probably_string_key(right) != 0) {
                return strcmp((const char*) left, (const char*) right) == 0;
            }
            return 0;
        }

        int javan_objects_equals(void* left, void* right) {
            return javan_object_equals(left, right);
        }

        int javan_object_compare_natural(void* left, void* right) {
            javan_objects_require_non_null(left);
            javan_objects_require_non_null(right);
            int left_type = javan_registered_type_id(left);
            int right_type = javan_registered_type_id(right);
            if (left_type == JAVAN_TYPE_JAVA_LANG_INTEGER && right_type == JAVAN_TYPE_JAVA_LANG_INTEGER) {
                int left_value = ((javan_boxed_int*) left)->value;
                int right_value = ((javan_boxed_int*) right)->value;
                if (left_value < right_value) {
                    return -1;
                }
                if (left_value > right_value) {
                    return 1;
                }
                return 0;
            }
            if (left_type == JAVAN_TYPE_JAVA_LANG_LONG && right_type == JAVAN_TYPE_JAVA_LANG_LONG) {
                long long left_value = ((javan_boxed_long*) left)->value;
                long long right_value = ((javan_boxed_long*) right)->value;
                if (left_value < right_value) {
                    return -1;
                }
                if (left_value > right_value) {
                    return 1;
                }
                return 0;
            }
            if (left_type == JAVAN_TYPE_JAVA_LANG_FLOAT && right_type == JAVAN_TYPE_JAVA_LANG_FLOAT) {
                return javan_float_compare(((javan_boxed_float*) left)->value, ((javan_boxed_float*) right)->value, 1);
            }
            if (left_type == JAVAN_TYPE_JAVA_LANG_DOUBLE && right_type == JAVAN_TYPE_JAVA_LANG_DOUBLE) {
                return javan_double_compare(((javan_boxed_double*) left)->value, ((javan_boxed_double*) right)->value, 1);
            }
            if (left_type == JAVAN_TYPE_JAVA_LANG_CHARACTER && right_type == JAVAN_TYPE_JAVA_LANG_CHARACTER) {
                int left_value = ((javan_boxed_character*) left)->value;
                int right_value = ((javan_boxed_character*) right)->value;
                if (left_value < right_value) {
                    return -1;
                }
                if (left_value > right_value) {
                    return 1;
                }
                return 0;
            }
            if (left_type == 0 && right_type == 0 && javan_probably_string_key(left) != 0 && javan_probably_string_key(right) != 0) {
                int compared = strcmp((const char*) left, (const char*) right);
                if (compared < 0) {
                    return -1;
                }
                if (compared > 0) {
                    return 1;
                }
                return 0;
            }
            javan_panic("unsupported natural comparator operand");
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

        static int javan_list_is_reversed(javan_object_list* list) {
            return list != NULL && (list->view_flags & JAVAN_LIST_VIEW_REVERSED) != 0;
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

        static int javan_list_backing_index(javan_object_list* list, int index) {
            int length = javan_list_logical_length(list);
            if (javan_list_is_reversed(list) != 0) {
                return length - 1 - index;
            }
            return index;
        }

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

        static void* javan_list_get_unchecked(javan_object_list* list, int index) {
            if (list->backing != NULL) {
                return javan_list_get_unchecked(list->backing, javan_list_backing_index(list, index));
            }
            return list->values[index];
        }

        static void javan_list_insert_base(javan_object_list* list, int index, void* element) {
            void* element_root = element;
            void** roots[] = {
                (void**) &list,
                (void**) &element_root
            };
            javan_root_frame_push(roots, 2);
            javan_list_ensure_capacity(list, list->length + 1);
            if (index < list->length) {
                memmove(list->values + index + 1, list->values + index, (unsigned long) (list->length - index) * sizeof(void*));
            }
            list->values[index] = element_root;
            list->length++;
            javan_root_frame_pop(roots);
            list->mod_count++;
        }

        static void* javan_list_set_base(javan_object_list* list, int index, void* element) {
            void* previous = list->values[index];
            list->values[index] = element;
            return previous;
        }

        static void* javan_list_remove_base(javan_object_list* list, int index) {
            void* previous = list->values[index];
            for (int cursor = index + 1; cursor < list->length; cursor++) {
                list->values[cursor - 1] = list->values[cursor];
            }
            list->length--;
            list->values[list->length] = NULL;
            list->mod_count++;
            return previous;
        }

        static void javan_list_clear_base(javan_object_list* list) {
            for (int index = 0; index < list->length; index++) {
                list->values[index] = NULL;
            }
            list->length = 0;
            list->mod_count++;
        }

        static void javan_list_add_logical(javan_object_list* list, int index, void* element) {
            javan_list_mutable_checked(list);
            int length = javan_list_logical_length(list);
            if (index < 0 || index > length) {
                javan_panic("list index out of bounds");
            }
            if (list->backing != NULL) {
                int backing_index = javan_list_is_reversed(list) != 0
                    ? javan_list_logical_length(list->backing) - index
                    : index;
                javan_list_add_logical(list->backing, backing_index, element);
                return;
            }
            javan_list_insert_base(list, index, element);
        }

        static void* javan_list_set_logical(javan_object_list* list, int index, void* element) {
            javan_list_mutable_checked(list);
            javan_list_bounds_checked(list, index);
            if (list->backing != NULL) {
                return javan_list_set_logical(list->backing, javan_list_backing_index(list, index), element);
            }
            return javan_list_set_base(list, index, element);
        }

        static void* javan_list_remove_logical(javan_object_list* list, int index) {
            javan_list_mutable_checked(list);
            javan_list_bounds_checked(list, index);
            if (list->backing != NULL) {
                return javan_list_remove_logical(list->backing, javan_list_backing_index(list, index));
            }
            return javan_list_remove_base(list, index);
        }

        static void javan_list_clear_logical(javan_object_list* list) {
            javan_list_mutable_checked(list);
            if (list->backing != NULL) {
                javan_list_clear_logical(list->backing);
                return;
            }
            javan_list_clear_base(list);
        }

        void* javan_arraylist_new(void) {
            return javan_list_new_with_capacity(0, 0);
        }

        void* javan_intstream_range(int start_inclusive, int end_exclusive) {
            javan_object_list* list = javan_list_checked(javan_arraylist_new());
            void* list_root = (void*) list;
            void* boxed_root = NULL;
            void** roots[] = {
                &list_root,
                &boxed_root
            };
            javan_root_frame_push(roots, 2);
            for (int value = start_inclusive; value < end_exclusive; value++) {
                boxed_root = javan_integer_value_of(value);
                javan_list_add_logical(list, javan_list_logical_length(list), boxed_root);
            }
            javan_root_frame_pop(roots);
            return list_root;
        }

        int javan_arraylist_add(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_add_logical(list, javan_list_logical_length(list), element);
            return 1;
        }

        void javan_arraylist_add_at(void* value, int index, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_add_logical(list, index, element);
        }

        int javan_arraylist_add_all(void* value, void* collection) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* source = javan_list_checked(collection);
            javan_list_mutable_checked(list);
            int copied = javan_list_logical_length(source);
            if (copied == 0) {
                return 0;
            }
            void* copied_values[copied > 0 ? copied : 1];
            void** roots[copied + 2];
            roots[0] = (void**) &list;
            roots[1] = (void**) &source;
            for (int index = 0; index < copied; index++) {
                copied_values[index] = javan_list_get_unchecked(source, index);
                roots[index + 2] = &copied_values[index];
            }
            javan_root_frame_push(roots, copied + 2);
            for (int index = 0; index < copied; index++) {
                javan_list_add_logical(list, javan_list_logical_length(list), copied_values[index]);
            }
            javan_root_frame_pop(roots);
            return 1;
        }

        void javan_arraylist_add_first(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_add_logical(list, 0, element);
        }

        void* javan_arraylist_set(void* value, int index, void* element) {
            return javan_list_set_logical(javan_list_checked(value), index, element);
        }

        void* javan_arraylist_remove_last(void* value) {
            javan_object_list* list = javan_list_checked(value);
            int length = javan_list_logical_length(list);
            if (length == 0) {
                javan_panic("list is empty");
            }
            return javan_list_remove_logical(list, length - 1);
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

        void* javan_list_unmodifiable(void* value) {
            javan_object_list* list = javan_list_checked(value);
            if (list->immutable != 0 && (list->view_flags & JAVAN_LIST_VIEW_REVERSED) == 0) {
                return list;
            }
            return javan_list_new_view(list, 1, JAVAN_LIST_VIEW_UNMODIFIABLE);
        }

        void* javan_list_reversed(void* value) {
            javan_object_list* list = javan_list_checked(value);
            return javan_list_new_view(list, list->immutable, JAVAN_LIST_VIEW_REVERSED);
        }

        void* javan_list_to_array(void* value) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_array* result = NULL;
            void** javan_list_to_array_roots[] = {
                (void**) &list,
                (void**) &result
            };
            javan_root_frame_push(javan_list_to_array_roots, 2);
            int length = javan_list_logical_length(list);
            result = (javan_object_array*) javan_object_array_new(length);
            for (int index = 0; index < length; index++) {
                result->values[index] = javan_list_get_unchecked(list, index);
            }
            javan_root_frame_pop(javan_list_to_array_roots);
            return result;
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

        int javan_list_contains_all(void* value, void* other) {
            javan_object_list* list = javan_list_checked(value);
            javan_object_list* other_list = javan_list_checked(other);
            void* iterator = NULL;
            void* element = NULL;
            void** javan_list_contains_all_roots[] = {
                (void**) &list,
                (void**) &other_list,
                (void**) &iterator,
                (void**) &element
            };
            javan_root_frame_push(javan_list_contains_all_roots, 4);
            iterator = javan_list_iterator(other_list);
            while (javan_iterator_has_next(iterator) != 0) {
                element = javan_iterator_next(iterator);
                if (javan_list_contains(list, element) == 0) {
                    javan_root_frame_pop(javan_list_contains_all_roots);
                    return 0;
                }
            }
            javan_root_frame_pop(javan_list_contains_all_roots);
            return 1;
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

        int javan_list_remove(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            int length = javan_list_logical_length(list);
            for (int index = 0; index < length; index++) {
                if (javan_object_equals(javan_list_get_unchecked(list, index), element) == 0) {
                    continue;
                }
                javan_list_remove_logical(list, index);
                return 1;
            }
            return 0;
        }

        void javan_list_clear(void* value) {
            javan_list_clear_logical(javan_list_checked(value));
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
            iterator->expected_mod_count = javan_list_observed_mod_count(list);
            iterator->reserved = 0;
            iterator->list = list;
            javan_update_runtime_allocation_kind((void*) iterator, JAVAN_RUNTIME_KIND_OBJECT_ITERATOR);
            javan_root_frame_pop(javan_list_iterator_roots);
            return iterator;
        }

        void* javan_hashset_new(void) {
            javan_object_list* set = javan_list_new_with_capacity(0, 0);
            javan_update_runtime_allocation_kind((void*) set, JAVAN_RUNTIME_KIND_OBJECT_SET);
            return set;
        }

        int javan_collection_add(void* value, void* element) {
            int runtime_kind = javan_runtime_kind_of(value);
            if (runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_LIST) {
                return javan_arraylist_add(value, element);
            }
            if (runtime_kind == JAVAN_RUNTIME_KIND_OBJECT_SET) {
                return javan_set_add(value, element);
            }
            javan_panic("unsupported collection object");
            return 0;
        }

        int javan_set_add(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            for (int index = 0; index < list->length; index++) {
                if (javan_object_equals(list->values[index], element) != 0) {
                    return 0;
                }
            }
            javan_list_append_raw(list, element);
            list->mod_count++;
            return 1;
        }

        int javan_set_remove(void* value, void* element) {
            javan_object_list* list = javan_list_checked(value);
            javan_list_mutable_checked(list);
            for (int index = 0; index < list->length; index++) {
                if (javan_object_equals(list->values[index], element) == 0) {
                    continue;
                }
                for (int cursor = index + 1; cursor < list->length; cursor++) {
                    list->values[cursor - 1] = list->values[cursor];
                }
                list->length--;
                list->values[list->length] = NULL;
                list->mod_count++;
                return 1;
            }
            return 0;
        }

        int javan_set_add_all(void* value, void* collection) {
            javan_object_list* set = javan_list_checked(value);
            javan_object_list* source = javan_list_checked(collection);
            javan_list_mutable_checked(set);
            int copied = javan_list_logical_length(source);
            if (copied == 0) {
                return 0;
            }
            void* copied_values[copied > 0 ? copied : 1];
            void** roots[copied + 2];
            roots[0] = (void**) &set;
            roots[1] = (void**) &source;
            for (int index = 0; index < copied; index++) {
                copied_values[index] = javan_list_get_unchecked(source, index);
                roots[index + 2] = &copied_values[index];
            }
            javan_root_frame_push(roots, copied + 2);
            int changed = 0;
            for (int index = 0; index < copied; index++) {
                if (javan_set_add(set, copied_values[index]) != 0) {
                    changed = 1;
                }
            }
            javan_root_frame_pop(roots);
            return changed;
        }

        void* javan_set_to_array(void* value) {
            return javan_list_to_array(value);
        }

        int javan_iterator_has_next(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            return iterator->index < javan_list_logical_length(iterator->list);
        }

        void* javan_iterator_next(void* value) {
            javan_object_iterator* iterator = javan_iterator_checked(value);
            if (iterator->expected_mod_count != javan_list_observed_mod_count(iterator->list)) {
                javan_panic("concurrent list modification");
            }
            if (iterator->index >= javan_list_logical_length(iterator->list)) {
                javan_panic("iterator exhausted");
            }
            void* result = javan_list_get_unchecked(iterator->list, iterator->index);
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

        void* javan_hashmap_new_typed(int type_id) {
            void* map = javan_map_new_with_capacity(0, 0);
            javan_register_object(map, type_id);
            return map;
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

        void javan_map_put_all(void* target_value, void* source_value) {
            javan_object_map* target = javan_map_checked(target_value);
            javan_object_map* source = javan_map_checked(source_value);
            javan_map_mutable_checked(target);
            void* key_root = NULL;
            void* value_root = NULL;
            void** javan_map_put_all_roots[] = {
                (void**) &target,
                (void**) &source,
                (void**) &key_root,
                (void**) &value_root
            };
            javan_root_frame_push(javan_map_put_all_roots, 4);
            for (int index = 0; index < source->length; index++) {
                key_root = source->keys[index];
                value_root = source->values[index];
                javan_map_put(target, key_root, value_root);
            }
            javan_root_frame_pop(javan_map_put_all_roots);
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

        void javan_map_clear(void* value) {
            javan_object_map* map = javan_map_checked(value);
            javan_map_mutable_checked(map);
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

        int javan_map_contains_key(void* value, void* key) {
            return javan_map_find(javan_map_checked(value), key) >= 0;
        }

        int javan_map_size(void* value) {
            return javan_map_checked(value)->length;
        }

        int javan_map_is_empty(void* value) {
            return javan_map_checked(value)->length == 0;
        }

        void* javan_map_entry_set(void* value) {
            javan_object_map* map = javan_map_checked(value);
            javan_object_list* list = NULL;
            javan_object_array* entry = NULL;
            void** javan_map_entry_set_roots[] = {
                (void**) &map,
                (void**) &list,
                (void**) &entry
            };
            javan_root_frame_push(javan_map_entry_set_roots, 3);
            list = javan_list_new_with_capacity(map->length, 1);
            for (int index = 0; index < map->length; index++) {
                entry = javan_object_array_new(2);
                entry->values[0] = map->keys[index];
                entry->values[1] = map->values[index];
                javan_list_append_raw(list, entry);
                entry = NULL;
            }
            javan_root_frame_pop(javan_map_entry_set_roots);
            return list;
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
        String result = SOURCE_HEAP;
        result = result + SOURCE_HEAP_VALIDATION;
        return result;
    }

    static String heapAlloc() {
        String result = SOURCE_HEAP_ALLOC_HEAD;
        result = result + SOURCE_HEAP_ALLOC_HEAD_CONT;
        result = result + SOURCE_HEAP_ALLOC_EXECUTOR;
        result = result + SOURCE_HEAP_ALLOC_EXECUTOR_CONT;
        result = result + SOURCE_HEAP_ALLOC_TAIL;
        result = result + SOURCE_HEAP_ALLOC_TIME;
        result = result + SOURCE_HEAP_ALLOC_TAIL_CONT;
        result = result + SOURCE_HEAP_ALLOC_TAIL_CONT_THREADS;
        return result;
    }

    static String arrays() {
        return SOURCE_ARRAYS;
    }

    static String collections() {
        return SOURCE_COLLECTIONS;
    }
}
