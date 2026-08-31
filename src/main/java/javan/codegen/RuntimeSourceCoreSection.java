package javan.codegen;

final class RuntimeSourceCoreSection {
    private static final String MAIN = """
        #include "javan_runtime.h"
        #include <dirent.h>
        #include <errno.h>
        #include <float.h>
        #include <limits.h>
        #include <math.h>
        #include <signal.h>
        #include <setjmp.h>
        #include <stdarg.h>
        #include <stdint.h>
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        #include <wchar.h>
        #if defined(_WIN32)
        #include <winsock2.h>
        #include <ws2tcpip.h>
        #include <windows.h>
        #include <shellapi.h>
        #include <process.h>
        #include <io.h>
        #include <sys/time.h>
        #else
        #include <arpa/inet.h>
        #include <fcntl.h>
        #include <netinet/in.h>
        #include <netinet/tcp.h>
        #include <pthread.h>
        #include <sched.h>
        #include <sys/socket.h>
        #include <sys/wait.h>
        #include <unistd.h>
        #endif
        #include <sys/stat.h>
        #include <sys/time.h>
        #include <time.h>
        typedef uintptr_t javan_socket_handle;
        #define JAVAN_SOCKET_INVALID ((javan_socket_handle) -1)
        #if defined(_WIN32)
        typedef int javan_socket_length;
        #else
        typedef socklen_t javan_socket_length;
        #endif
        #if defined(__FAST_MATH__) || (defined(__FINITE_MATH_ONLY__) && __FINITE_MATH_ONLY__ != 0)
        #error "Javan requires strict floating-point compiler mode"
        #endif
        #if !defined(FLT_HAS_SUBNORM) || FLT_HAS_SUBNORM != 1 || !defined(DBL_HAS_SUBNORM) || DBL_HAS_SUBNORM != 1
        #error "Javan requires binary32 and binary64 subnormal support"
        #endif
        #if CHAR_BIT != 8 || FLT_RADIX != 2 || DBL_MANT_DIG != 53 || DBL_MIN_EXP != -1021 || DBL_MAX_EXP != 1024
        #error "Javan requires IEEE 754 binary64 double"
        #endif
        #if FLT_RADIX != 2 || FLT_MANT_DIG != 24 || FLT_MIN_EXP != -125 || FLT_MAX_EXP != 128
        #error "Javan requires IEEE 754 binary32 float"
        #endif
        _Static_assert(sizeof(uint32_t) == 4, "Javan requires 32-bit uint32_t");
        _Static_assert(sizeof(uint64_t) == 8, "Javan requires 64-bit uint64_t");
        _Static_assert(sizeof(int) == 4, "Javan requires 32-bit int");
        _Static_assert(sizeof(long long) == 8, "Javan requires 64-bit long long");
        _Static_assert(sizeof(double) == 8, "Javan requires 64-bit double");
        _Static_assert(sizeof(float) == 4, "Javan requires 32-bit float");
        #if defined(_MSC_VER)
        #define JAVAN_THREAD_LOCAL __declspec(thread)
        #else
        #define JAVAN_THREAD_LOCAL _Thread_local
        #endif

        static char* javan_string_alloc(unsigned long size);
        static void* javan_string_copy(const char* value);
        static int javan_socket_native_close(javan_socket_handle fd);
        static void javan_sleep_micros(unsigned long micros);
        static void javan_os_thread_yield(void);
        static void javan_cpu_spin_wait_hint(void);
        static JAVAN_THREAD_LOCAL char javan_last_error_value[512];
        static JAVAN_THREAD_LOCAL char javan_last_error_code_value[64];
        static JAVAN_THREAD_LOCAL char javan_last_error_summary_value[128];
        static JAVAN_THREAD_LOCAL char javan_last_error_class_value[160];
        static JAVAN_THREAD_LOCAL char javan_last_error_method_value[160];
        static JAVAN_THREAD_LOCAL char javan_last_error_file_value[160];
        static JAVAN_THREAD_LOCAL char javan_last_error_source_line_value[256];
        static JAVAN_THREAD_LOCAL char javan_last_error_why_value[256];
        static JAVAN_THREAD_LOCAL char javan_last_error_fix_value[256];
        static JAVAN_THREAD_LOCAL char javan_last_error_detail_value[256];
        static JAVAN_THREAD_LOCAL int javan_last_error_line_value = -1;
        static JAVAN_THREAD_LOCAL int javan_last_error_bytecode_offset_value = -1;
        static JAVAN_THREAD_LOCAL int javan_last_error_set = 0;
        static JAVAN_THREAD_LOCAL jmp_buf* javan_panic_target = NULL;
        static JAVAN_THREAD_LOCAL JavanSourceContext* javan_source_context_top = NULL;
        static char javan_runtime_executable_path[4096];

        #if defined(_WIN32)
        static wchar_t* javan_windows_utf8_to_wide_copy(const char* value) {
            if (value == NULL) {
                return NULL;
            }
            int length = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value, -1, NULL, 0);
            if (length <= 0 || (unsigned long) length > ULONG_MAX / sizeof(wchar_t)) {
                return NULL;
            }
            wchar_t* result = (wchar_t*) malloc((unsigned long) length * sizeof(wchar_t));
            if (result == NULL
                || MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value, -1, result, length) == 0) {
                free(result);
                return NULL;
            }
            return result;
        }

        static char* javan_windows_wide_to_utf8_copy(const wchar_t* value) {
            if (value == NULL) {
                return NULL;
            }
            int length = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value, -1, NULL, 0, NULL, NULL);
            if (length <= 0) {
                return NULL;
            }
            char* result = (char*) malloc((unsigned long) length);
            if (result == NULL
                || WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value, -1, result, length, NULL, NULL) == 0) {
                free(result);
                return NULL;
            }
            return result;
        }

        static FILE* javan_file_open(const char* path, const char* mode) {
            wchar_t* wide_path = javan_windows_utf8_to_wide_copy(path);
            wchar_t* wide_mode = javan_windows_utf8_to_wide_copy(mode);
            if (wide_path == NULL || wide_mode == NULL) {
                free(wide_path);
                free(wide_mode);
                return NULL;
            }
            FILE* result = _wfopen(wide_path, wide_mode);
            free(wide_path);
            free(wide_mode);
            return result;
        }

        static char** javan_windows_command_line_values = NULL;
        static char** javan_windows_command_line_owned_values = NULL;
        static int javan_windows_command_line_count = 0;

        static void javan_windows_release_command_line_values(char** values, char** owned_values, int count) {
            for (int index = 0; index < count; index++) {
                free(owned_values == NULL ? NULL : owned_values[index]);
            }
            free(values);
            free(owned_values);
        }

        void javan_runtime_prepare_command_line_args(int* argc, char*** argv) {
            if (argc == NULL || argv == NULL) {
                return;
            }
            int count = 0;
            wchar_t** wide_values = CommandLineToArgvW(GetCommandLineW(), &count);
            if (wide_values == NULL || count <= 0) {
                if (wide_values != NULL) {
                    LocalFree(wide_values);
                }
                javan_panic("Windows command line conversion failed");
                return;
            }
            char** values = (char**) calloc((unsigned long) count + 1UL, sizeof(char*));
            char** owned_values = (char**) calloc((unsigned long) count, sizeof(char*));
            if (values == NULL || owned_values == NULL) {
                javan_windows_release_command_line_values(values, owned_values, 0);
                LocalFree(wide_values);
                javan_panic("Windows command line allocation failed");
                return;
            }
            for (int index = 0; index < count; index++) {
                owned_values[index] = javan_windows_wide_to_utf8_copy(wide_values[index]);
                if (owned_values[index] == NULL) {
                    LocalFree(wide_values);
                    javan_windows_release_command_line_values(values, owned_values, index + 1);
                    javan_panic("Windows command line is not valid Unicode");
                    return;
                }
                values[index] = owned_values[index];
            }
            LocalFree(wide_values);
            javan_windows_command_line_values = values;
            javan_windows_command_line_owned_values = owned_values;
            javan_windows_command_line_count = count;
            if (atexit(javan_runtime_release_command_line_args) != 0) {
                javan_runtime_release_command_line_args();
                javan_panic("Windows command line cleanup registration failed");
                return;
            }
            *argc = count;
            *argv = values;
        }

        void javan_runtime_release_command_line_args(void) {
            javan_windows_release_command_line_values(
                javan_windows_command_line_values,
                javan_windows_command_line_owned_values,
                javan_windows_command_line_count
            );
            javan_windows_command_line_values = NULL;
            javan_windows_command_line_owned_values = NULL;
            javan_windows_command_line_count = 0;
        }
        #else
        static FILE* javan_file_open(const char* path, const char* mode) {
            return fopen(path, mode);
        }

        void javan_runtime_prepare_command_line_args(int* argc, char*** argv) {
            (void) argc;
            (void) argv;
        }

        void javan_runtime_release_command_line_args(void) {
        }
        #endif

        static void javan_sleep_micros(unsigned long micros) {
            if (micros == 0UL) {
                return;
            }
        #if defined(_WIN32)
            DWORD millis = (DWORD) ((micros + 999UL) / 1000UL);
            if (millis == 0U) {
                millis = 1U;
            }
            Sleep(millis);
        #else
            usleep((useconds_t) micros);
        #endif
        }

        static void javan_os_thread_yield(void) {
        #if defined(_WIN32)
            if (SwitchToThread() == 0) {
                Sleep(0U);
            }
        #else
            (void) sched_yield();
        #endif
        }

        static void javan_cpu_spin_wait_hint(void) {
        #if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
            YieldProcessor();
        #elif defined(__i386__) || defined(__x86_64__)
            __asm__ __volatile__("pause");
        #elif defined(__aarch64__) || defined(__arm__)
            __asm__ __volatile__("yield");
        #else
            (void) 0;
        #endif
        }

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

        void javan_runtime_set_executable_path(const char* argv0) {
            if (argv0 == NULL || argv0[0] == '\\0') {
                javan_runtime_executable_path[0] = '\\0';
                return;
            }
        #if defined(_WIN32)
            wchar_t wide_path[4096];
            DWORD length = GetModuleFileNameW(NULL, wide_path, (DWORD) (sizeof(wide_path) / sizeof(wide_path[0])));
            char* utf8_path = length == 0 || length >= sizeof(wide_path) / sizeof(wide_path[0])
                ? NULL
                : javan_windows_wide_to_utf8_copy(wide_path);
            if (utf8_path == NULL || strlen(utf8_path) >= sizeof(javan_runtime_executable_path)) {
                javan_copy_error_field(javan_runtime_executable_path, sizeof(javan_runtime_executable_path), argv0);
            } else {
                javan_copy_error_field(javan_runtime_executable_path, sizeof(javan_runtime_executable_path), utf8_path);
            }
            free(utf8_path);
        #else
            if (argv0[0] == '/') {
                javan_copy_error_field(javan_runtime_executable_path, sizeof(javan_runtime_executable_path), argv0);
                return;
            }
            if (strchr(argv0, '/') != NULL) {
                char current[4096];
                if (getcwd(current, sizeof(current)) != NULL) {
                    snprintf(javan_runtime_executable_path, sizeof(javan_runtime_executable_path), "%s/%s", current, argv0);
                    return;
                }
            }
            const char* path = getenv("PATH");
            if (path != NULL) {
                const char* start = path;
                while (start != NULL) {
                    const char* end = strchr(start, ':');
                    unsigned long length = end == NULL ? strlen(start) : (unsigned long) (end - start);
                    if (length > 0 && length + strlen(argv0) + 2 < sizeof(javan_runtime_executable_path)) {
                        memcpy(javan_runtime_executable_path, start, length);
                        javan_runtime_executable_path[length] = '/';
                        strcpy(javan_runtime_executable_path + length + 1, argv0);
                        if (access(javan_runtime_executable_path, X_OK) == 0) {
                            return;
                        }
                    }
                    start = end == NULL ? NULL : end + 1;
                }
            }
            javan_copy_error_field(javan_runtime_executable_path, sizeof(javan_runtime_executable_path), argv0);
        #endif
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

        static int javan_write_string_utf8(FILE* stream, const char* value) {
            const unsigned char* cursor = (const unsigned char*) (value == NULL ? "" : value);
            while (*cursor != 0U) {
                if (cursor[0] == 0xC0U && cursor[1] == 0x80U) {
                    fputc(0, stream);
                    cursor += 2;
                    continue;
                }
                if (cursor[0] == 0xEDU
                    && cursor[1] >= 0xA0U && cursor[1] <= 0xAFU
                    && (cursor[2] & 0xC0U) == 0x80U) {
                    unsigned int high = ((unsigned int) (cursor[0] & 0x0FU) << 12)
                        | ((unsigned int) (cursor[1] & 0x3FU) << 6)
                        | (unsigned int) (cursor[2] & 0x3FU);
                    cursor += 3;
                    if (cursor[0] == 0xEDU
                        && cursor[1] >= 0xB0U && cursor[1] <= 0xBFU
                        && (cursor[2] & 0xC0U) == 0x80U) {
                        unsigned int low = ((unsigned int) (cursor[0] & 0x0FU) << 12)
                            | ((unsigned int) (cursor[1] & 0x3FU) << 6)
                            | (unsigned int) (cursor[2] & 0x3FU);
                        unsigned int code_point = 0x10000U + ((high - 0xD800U) << 10) + (low - 0xDC00U);
                        fputc((int) (0xF0U | (code_point >> 18)), stream);
                        fputc((int) (0x80U | ((code_point >> 12) & 0x3FU)), stream);
                        fputc((int) (0x80U | ((code_point >> 6) & 0x3FU)), stream);
                        fputc((int) (0x80U | (code_point & 0x3FU)), stream);
                        cursor += 3;
                        continue;
                    }
                    fputc('?', stream);
                    continue;
                }
                if (cursor[0] == 0xEDU
                    && cursor[1] >= 0xB0U && cursor[1] <= 0xBFU
                    && (cursor[2] & 0xC0U) == 0x80U) {
                    fputc('?', stream);
                    cursor += 3;
                    continue;
                }
                fputc((int) *cursor, stream);
                cursor++;
            }
            return ferror(stream) == 0;
        }

        static void javan_write_printstream(FILE* stream, const char* value, int newline) {
            javan_runtime_lock_enter();
            (void) javan_write_string_utf8(stream, value);
            if (newline != 0) {
                fputc('\\n', stream);
            }
            fflush(stream);
            javan_runtime_lock_leave();
        }

        void javan_println(const char* value) {
            javan_write_printstream(stdout, value, 1);
        }

        void javan_print(const char* value) {
            javan_write_printstream(stdout, value, 0);
        }

        void javan_eprintln(const char* value) {
            javan_write_printstream(stderr, value, 1);
        }

        void javan_eprint(const char* value) {
            javan_write_printstream(stderr, value, 0);
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
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            javan_println(buffer);
        }

        void javan_eprintln_int(int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            javan_eprintln(buffer);
        }

        void javan_println_long(long long value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            javan_println(buffer);
        }

        void javan_eprintln_long(long long value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            javan_eprintln(buffer);
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
            javan_println(buffer);
        }

        void javan_eprintln_float(float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            javan_eprintln(buffer);
        }

        void javan_println_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            javan_println(buffer);
        }

        void javan_eprintln_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            javan_eprintln(buffer);
        }

        void javan_println_bool(int value) {
            javan_println(value == 0 ? "false" : "true");
        }

        void javan_eprintln_bool(int value) {
            javan_eprintln(value == 0 ? "false" : "true");
        }

        void javan_printstream_print(void* stream, const char* value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprint(value);
                return;
            }
            javan_print(value);
        }

        void javan_print_object_value(void* value) {
            javan_print((const char*) javan_printable_object_string(value));
        }

        void javan_eprint_object_value(void* value) {
            javan_eprint((const char*) javan_printable_object_string(value));
        }

        void javan_printstream_print_object(void* stream, void* value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprint_object_value(value);
                return;
            }
            javan_print_object_value(value);
        }

        void javan_printstream_println(void* stream, const char* value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln(value);
                return;
            }
            javan_println(value);
        }

        void javan_println_object_value(void* value) {
            javan_println((const char*) javan_printable_object_string(value));
        }

        void javan_eprintln_object_value(void* value) {
            javan_eprintln((const char*) javan_printable_object_string(value));
        }

        void javan_printstream_println_object(void* stream, void* value) {
            if (javan_printstream_is_err(stream)) {
                javan_eprintln_object_value(value);
                return;
            }
            javan_println_object_value(value);
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

        int javan_math_round_float(float value) {
            uint32_t bits = 0U;
            memcpy(&bits, &value, sizeof(bits));
            const uint32_t exponent = (bits >> 23U) & 0xffU;
            const uint32_t fraction = bits & 0x007fffffU;
            const int negative = (bits & 0x80000000U) != 0U;
            if (exponent == 0xffU) {
                if (fraction != 0U) {
                    return 0;
                }
                return negative ? INT_MIN : INT_MAX;
            }
            if (exponent == 0U) {
                return 0;
            }
            const int unbiased_exponent = (int) exponent - 127;
            if (unbiased_exponent < -1) {
                return 0;
            }
            if (unbiased_exponent >= 31) {
                return negative ? INT_MIN : INT_MAX;
            }
            const uint32_t significand = 0x00800000U | fraction;
            if (unbiased_exponent >= 23) {
                const uint32_t magnitude = significand << (unsigned int) (unbiased_exponent - 23);
                return negative ? -(int) magnitude : (int) magnitude;
            }
            const unsigned int fractional_shift = (unsigned int) (23 - unbiased_exponent);
            uint32_t magnitude = significand >> fractional_shift;
            const uint32_t fractional_mask = (1U << fractional_shift) - 1U;
            const uint32_t fractional_part = significand & fractional_mask;
            const uint32_t half = 1U << (fractional_shift - 1U);
            if ((!negative && fractional_part >= half)
                || (negative && fractional_part > half)) {
                magnitude++;
            }
            return negative ? -(int) magnitude : (int) magnitude;
        }

        long long javan_math_round_double(double value) {
            uint64_t bits = 0ULL;
            memcpy(&bits, &value, sizeof(bits));
            const uint64_t exponent = (bits >> 52U) & 0x7ffULL;
            const uint64_t fraction = bits & 0x000fffffffffffffULL;
            const int negative = (bits & 0x8000000000000000ULL) != 0ULL;
            if (exponent == 0x7ffULL) {
                if (fraction != 0ULL) {
                    return 0LL;
                }
                return negative ? LLONG_MIN : LLONG_MAX;
            }
            if (exponent == 0ULL) {
                return 0LL;
            }
            const int unbiased_exponent = (int) exponent - 1023;
            if (unbiased_exponent < -1) {
                return 0LL;
            }
            if (unbiased_exponent >= 63) {
                return negative ? LLONG_MIN : LLONG_MAX;
            }
            const uint64_t significand = 0x0010000000000000ULL | fraction;
            if (unbiased_exponent >= 52) {
                const uint64_t magnitude = significand << (unsigned int) (unbiased_exponent - 52);
                return negative ? -(long long) magnitude : (long long) magnitude;
            }
            const unsigned int fractional_shift = (unsigned int) (52 - unbiased_exponent);
            uint64_t magnitude = significand >> fractional_shift;
            const uint64_t fractional_mask = (1ULL << fractional_shift) - 1ULL;
            const uint64_t fractional_part = significand & fractional_mask;
            const uint64_t half = 1ULL << (fractional_shift - 1U);
            if ((!negative && fractional_part >= half)
                || (negative && fractional_part > half)) {
                magnitude++;
            }
            return negative ? -(long long) magnitude : (long long) magnitude;
        }

        double javan_math_floor_double(double value) {
            uint64_t bits = UINT64_C(0);
            memcpy(&bits, &value, sizeof(bits));
            const uint64_t exponent = (bits >> 52U) & UINT64_C(0x7ff);
            if (exponent == UINT64_C(0x7ff)) {
                return value;
            }
            const uint64_t magnitude = bits & UINT64_C(0x7fffffffffffffff);
            if (magnitude == UINT64_C(0)) {
                return value;
            }
            const int negative = (bits & UINT64_C(0x8000000000000000)) != UINT64_C(0);
            if (exponent == UINT64_C(0)) {
                bits = negative ? UINT64_C(0xbff0000000000000) : UINT64_C(0);
            } else {
                const int unbiased_exponent = (int) exponent - 1023;
                if (unbiased_exponent < 0) {
                    bits = negative ? UINT64_C(0xbff0000000000000) : UINT64_C(0);
                } else if (unbiased_exponent < 52) {
                    const unsigned int fractional_shift = (unsigned int) (52 - unbiased_exponent);
                    const uint64_t fractional_mask = (UINT64_C(1) << fractional_shift) - UINT64_C(1);
                    if ((bits & fractional_mask) != UINT64_C(0)) {
                        bits &= ~fractional_mask;
                        if (negative) {
                            bits += UINT64_C(1) << fractional_shift;
                        }
                    }
                }
            }
            memcpy(&value, &bits, sizeof(value));
            return value;
        }

        double javan_math_ceil_double(double value) {
            uint64_t bits = UINT64_C(0);
            memcpy(&bits, &value, sizeof(bits));
            const uint64_t exponent = (bits >> 52U) & UINT64_C(0x7ff);
            if (exponent == UINT64_C(0x7ff)) {
                return value;
            }
            const uint64_t sign_mask = UINT64_C(0x8000000000000000);
            const uint64_t magnitude = bits & ~sign_mask;
            if (magnitude == UINT64_C(0)) {
                return value;
            }
            const uint64_t negative = bits & sign_mask;
            if (exponent < UINT64_C(1023)) {
                bits = negative == UINT64_C(0) ? UINT64_C(0x3ff0000000000000) : sign_mask;
            } else if (exponent < UINT64_C(1075)) {
                const uint64_t fractional_shift = UINT64_C(1075) - exponent;
                const uint64_t fractional_mask = (UINT64_C(1) << fractional_shift) - UINT64_C(1);
                if ((bits & fractional_mask) != UINT64_C(0)) {
                    bits &= ~fractional_mask;
                    if (negative == UINT64_C(0)) {
                        bits += UINT64_C(1) << fractional_shift;
                    }
                }
            }
            memcpy(&value, &bits, sizeof(value));
            return value;
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

        float javan_math_abs_float(float value) {
            return fabsf(value);
        }

        double javan_math_abs_double(double value) {
            return fabs(value);
        }

        int javan_math_min_int(int left, int right) {
            return left <= right ? left : right;
        }

        long long javan_math_min_long(long long left, long long right) {
            return left <= right ? left : right;
        }

        float javan_math_min_float(float left, float right) {
            if (isnan(left)) {
                return left;
            }
            if (isnan(right)) {
                return right;
            }
            if (left == 0.0f && right == 0.0f) {
                return signbit(left) ? left : right;
            }
            return left <= right ? left : right;
        }

        double javan_math_min_double(double left, double right) {
            if (isnan(left)) {
                return left;
            }
            if (isnan(right)) {
                return right;
            }
            if (left == 0.0 && right == 0.0) {
                return signbit(left) ? left : right;
            }
            return left <= right ? left : right;
        }

        int javan_math_max_int(int left, int right) {
            return left >= right ? left : right;
        }

        long long javan_math_max_long(long long left, long long right) {
            return left >= right ? left : right;
        }

        float javan_math_max_float(float left, float right) {
            if (isnan(left)) {
                return left;
            }
            if (isnan(right)) {
                return right;
            }
            if (left == 0.0f && right == 0.0f) {
                return signbit(left) ? right : left;
            }
            return left >= right ? left : right;
        }

        double javan_math_max_double(double left, double right) {
            if (isnan(left)) {
                return left;
            }
            if (isnan(right)) {
                return right;
            }
            if (left == 0.0 && right == 0.0) {
                return signbit(left) ? right : left;
            }
            return left >= right ? left : right;
        }

        int javan_math_add_exact_int_overflows(int left, int right) {
            const long long result = (long long) left + (long long) right;
            return result < INT_MIN || result > INT_MAX;
        }

        int javan_math_add_exact_int(int left, int right) {
            const long long result = (long long) left + (long long) right;
            return (int) result;
        }

        int javan_math_add_exact_long_overflows(long long left, long long right) {
            return (right > 0 && left > LLONG_MAX - right)
                || (right < 0 && left < LLONG_MIN - right);
        }

        long long javan_math_add_exact_long(long long left, long long right) {
            return left + right;
        }

        int javan_math_subtract_exact_int_overflows(int left, int right) {
            const long long result = (long long) left - (long long) right;
            return result < INT_MIN || result > INT_MAX;
        }

        int javan_math_subtract_exact_int(int left, int right) {
            const long long result = (long long) left - (long long) right;
            return (int) result;
        }

        int javan_math_subtract_exact_long_overflows(long long left, long long right) {
            return (right > 0 && left < LLONG_MIN + right)
                || (right < 0 && left > LLONG_MAX + right);
        }

        long long javan_math_subtract_exact_long(long long left, long long right) {
            return left - right;
        }

        int javan_math_multiply_exact_int_overflows(int left, int right) {
            const long long result = (long long) left * (long long) right;
            return result < INT_MIN || result > INT_MAX;
        }

        int javan_math_multiply_exact_int(int left, int right) {
            const long long result = (long long) left * (long long) right;
            return (int) result;
        }

        int javan_math_multiply_exact_long_int_overflows(long long left, int right) {
            if (left == 0 || right == 0) {
                return 0;
            }
            if (left > 0) {
                return right > 0
                    ? left > LLONG_MAX / (long long) right
                    : (long long) right < LLONG_MIN / left;
            }
            return right > 0
                ? left < LLONG_MIN / (long long) right
                : left < LLONG_MAX / (long long) right;
        }

        long long javan_math_multiply_exact_long_int(long long left, int right) {
            return left * (long long) right;
        }

        int javan_math_multiply_exact_long_long_overflows(long long left, long long right) {
            if (left == 0 || right == 0) {
                return 0;
            }
            if (left > 0) {
                return right > 0
                    ? left > LLONG_MAX / right
                    : right < LLONG_MIN / left;
            }
            return right > 0
                ? left < LLONG_MIN / right
                : left < LLONG_MAX / right;
        }

        long long javan_math_multiply_exact_long_long(long long left, long long right) {
            return left * right;
        }

        int javan_math_increment_exact_int_overflows(int value) {
            return value == INT_MAX;
        }

        int javan_math_increment_exact_int(int value) {
            return value + 1;
        }

        int javan_math_increment_exact_long_overflows(long long value) {
            return value == LLONG_MAX;
        }

        long long javan_math_increment_exact_long(long long value) {
            return value + 1LL;
        }

        int javan_math_decrement_exact_int_overflows(int value) {
            return value == INT_MIN;
        }

        int javan_math_decrement_exact_int(int value) {
            return value - 1;
        }

        int javan_math_decrement_exact_long_overflows(long long value) {
            return value == LLONG_MIN;
        }

        long long javan_math_decrement_exact_long(long long value) {
            return value - 1LL;
        }

        int javan_math_negate_exact_int_overflows(int value) {
            return value == INT_MIN;
        }

        int javan_math_negate_exact_int(int value) {
            return -value;
        }

        int javan_math_negate_exact_long_overflows(long long value) {
            return value == LLONG_MIN;
        }

        long long javan_math_negate_exact_long(long long value) {
            return -value;
        }

        int javan_math_to_int_exact_overflows(long long value) {
            return value < INT_MIN || value > INT_MAX;
        }

        int javan_math_to_int_exact(long long value) {
            return (int) value;
        }

        static int javan_int_from_bits(uint32_t bits) {
            int result = 0;
            memcpy(&result, &bits, sizeof(result));
            return result;
        }

        static long long javan_long_from_bits(uint64_t bits) {
            long long result = 0;
            memcpy(&result, &bits, sizeof(result));
            return result;
        }

        int javan_int_neg(int value) {
            return javan_int_from_bits(0U - (uint32_t) value);
        }

        int javan_int_add_wrapping(int left, int right) {
            return javan_int_from_bits((uint32_t) left + (uint32_t) right);
        }

        int javan_int_subtract_wrapping(int left, int right) {
            return javan_int_from_bits((uint32_t) left - (uint32_t) right);
        }

        int javan_int_multiply_wrapping(int left, int right) {
            return javan_int_from_bits((uint32_t) left * (uint32_t) right);
        }

        int javan_int_divide(int left, int right) {
            if (right == 0) {
                return 0;
            }
            if (left == INT_MIN && right == -1) {
                return INT_MIN;
            }
            return left / right;
        }

        int javan_int_remainder(int left, int right) {
            if (right == 0 || (left == INT_MIN && right == -1)) {
                return 0;
            }
            return left % right;
        }

        long long javan_long_neg(long long value) {
            return javan_long_from_bits(UINT64_C(0) - (uint64_t) value);
        }

        long long javan_long_add_wrapping(long long left, long long right) {
            return javan_long_from_bits((uint64_t) left + (uint64_t) right);
        }

        long long javan_long_subtract_wrapping(long long left, long long right) {
            return javan_long_from_bits((uint64_t) left - (uint64_t) right);
        }

        long long javan_long_multiply_wrapping(long long left, long long right) {
            return javan_long_from_bits((uint64_t) left * (uint64_t) right);
        }

        long long javan_long_divide(long long left, long long right) {
            if (right == 0LL) {
                return 0LL;
            }
            if (left == LLONG_MIN && right == -1LL) {
                return LLONG_MIN;
            }
            return left / right;
        }

        long long javan_long_remainder(long long left, long long right) {
            if (right == 0LL || (left == LLONG_MIN && right == -1LL)) {
                return 0LL;
            }
            return left % right;
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

        double javan_f2d(float value) {
            return (double) value;
        }

        void javan_runtime_validate_floating_layout(void) {
            const double double_one = 1.0;
            const double double_negative_zero = -0.0;
            const double double_min_subnormal = 0x1p-1074;
            const double double_infinity = INFINITY;
            const double double_nan = NAN;
            const float float_one = 1.0f;
            const float float_negative_zero = -0.0f;
            const float float_min_subnormal = 0x1p-149f;
            const float float_infinity = INFINITY;
            const float float_nan = NAN;
            uint64_t double_one_bits = UINT64_C(0);
            uint64_t double_negative_zero_bits = UINT64_C(0);
            uint64_t double_min_subnormal_bits = UINT64_C(0);
            uint64_t double_infinity_bits = UINT64_C(0);
            uint32_t float_one_bits = UINT32_C(0);
            uint32_t float_negative_zero_bits = UINT32_C(0);
            uint32_t float_min_subnormal_bits = UINT32_C(0);
            uint32_t float_infinity_bits = UINT32_C(0);
            memcpy(&double_one_bits, &double_one, sizeof(double_one_bits));
            memcpy(&double_negative_zero_bits, &double_negative_zero, sizeof(double_negative_zero_bits));
            memcpy(&double_min_subnormal_bits, &double_min_subnormal, sizeof(double_min_subnormal_bits));
            memcpy(&double_infinity_bits, &double_infinity, sizeof(double_infinity_bits));
            memcpy(&float_one_bits, &float_one, sizeof(float_one_bits));
            memcpy(&float_negative_zero_bits, &float_negative_zero, sizeof(float_negative_zero_bits));
            memcpy(&float_min_subnormal_bits, &float_min_subnormal, sizeof(float_min_subnormal_bits));
            memcpy(&float_infinity_bits, &float_infinity, sizeof(float_infinity_bits));
            if (double_one_bits != UINT64_C(0x3ff0000000000000)
                || double_negative_zero_bits != UINT64_C(0x8000000000000000)
                || double_min_subnormal_bits != UINT64_C(0x0000000000000001)
                || double_infinity_bits != UINT64_C(0x7ff0000000000000)
                || double_nan == double_nan
                || float_one_bits != UINT32_C(0x3f800000)
                || float_negative_zero_bits != UINT32_C(0x80000000)
                || float_min_subnormal_bits != UINT32_C(0x00000001)
                || float_infinity_bits != UINT32_C(0x7f800000)
                || float_nan == float_nan) {
                javan_panic("Javan requires IEEE binary32 and binary64 floating object layout");
            }
        }

        static uint64_t javan_round_right_even(uint64_t value, unsigned int shift) {
            const uint64_t truncated = value >> shift;
            const uint64_t remainder_mask = (UINT64_C(1) << shift) - UINT64_C(1);
            const uint64_t remainder = value & remainder_mask;
            const uint64_t halfway = UINT64_C(1) << (shift - 1U);
            return truncated + (remainder > halfway || (remainder == halfway && (truncated & UINT64_C(1)) != UINT64_C(0)));
        }

        float javan_d2f(double value) {
            uint64_t input_bits = UINT64_C(0);
            memcpy(&input_bits, &value, sizeof(input_bits));
            const uint32_t sign = (uint32_t) (input_bits >> 63U) << 31U;
            const uint32_t double_exponent = (uint32_t) ((input_bits >> 52U) & UINT64_C(0x7ff));
            const uint64_t double_fraction = input_bits & UINT64_C(0x000fffffffffffff);
            uint32_t output_bits = sign;

            if (double_exponent == UINT32_C(0x7ff)) {
                /* Java narrowing guarantees a float NaN; Javan chooses one positive canonical binary32 NaN. */
                output_bits = double_fraction == UINT64_C(0)
                    ? sign | UINT32_C(0x7f800000)
                    : UINT32_C(0x7fc00000);
            } else if (double_exponent != UINT32_C(0) || double_fraction != UINT64_C(0)) {
                uint64_t significand = double_fraction;
                unsigned int leading_bit = 0U;
                int exponent;
                if (double_exponent == UINT32_C(0)) {
                    uint64_t remaining = significand;
                    while (remaining > UINT64_C(1)) {
                        remaining >>= 1U;
                        leading_bit++;
                    }
                    exponent = (int) leading_bit - 1074;
                } else {
                    significand |= UINT64_C(1) << 52U;
                    leading_bit = 52U;
                    exponent = (int) double_exponent - 1023;
                }
                if (exponent > 127) {
                    output_bits = sign | UINT32_C(0x7f800000);
                } else if (exponent >= -126) {
                    uint64_t rounded = significand;
                    if (leading_bit > 23U) {
                        rounded = javan_round_right_even(rounded, leading_bit - 23U);
                    } else {
                        rounded <<= 23U - leading_bit;
                    }
                    if (rounded == (UINT64_C(1) << 24U)) {
                        rounded >>= 1U;
                        exponent++;
                    }
                    output_bits = exponent > 127
                        ? sign | UINT32_C(0x7f800000)
                        : sign | ((uint32_t) (exponent + 127) << 23U) | ((uint32_t) rounded & UINT32_C(0x007fffff));
                } else if (exponent >= -150) {
                    const unsigned int shift = (unsigned int) (-exponent - 97);
                    const uint64_t rounded = javan_round_right_even(significand, shift);
                    output_bits = rounded >= (UINT64_C(1) << 23U)
                        ? sign | UINT32_C(0x00800000)
                        : sign | (uint32_t) rounded;
                }
            }
            float result = 0.0f;
            memcpy(&result, &output_bits, sizeof(result));
            return result;
        }

        double javan_l2d(long long value) {
            const int negative = value < 0;
            const uint64_t magnitude = negative
                ? UINT64_C(0) - (uint64_t) value
                : (uint64_t) value;
            uint64_t bits = negative ? UINT64_C(0x8000000000000000) : UINT64_C(0);
            if (magnitude == UINT64_C(0)) {
                double result = 0.0;
                memcpy(&result, &bits, sizeof(result));
                return result;
            }

            unsigned int exponent = 0U;
            uint64_t remaining = magnitude;
            if (remaining >= (UINT64_C(1) << 32U)) {
                remaining >>= 32U;
                exponent += 32U;
            }
            if (remaining >= (UINT64_C(1) << 16U)) {
                remaining >>= 16U;
                exponent += 16U;
            }
            if (remaining >= (UINT64_C(1) << 8U)) {
                remaining >>= 8U;
                exponent += 8U;
            }
            if (remaining >= (UINT64_C(1) << 4U)) {
                remaining >>= 4U;
                exponent += 4U;
            }
            if (remaining >= (UINT64_C(1) << 2U)) {
                remaining >>= 2U;
                exponent += 2U;
            }
            if (remaining >= (UINT64_C(1) << 1U)) {
                exponent += 1U;
            }

            uint64_t significand;
            if (exponent <= 52U) {
                significand = magnitude << (52U - exponent);
            } else {
                const unsigned int discarded_shift = exponent - 52U;
                const uint64_t discarded_mask = (UINT64_C(1) << discarded_shift) - UINT64_C(1);
                const uint64_t discarded = magnitude & discarded_mask;
                significand = magnitude >> discarded_shift;
                const uint64_t halfway = UINT64_C(1) << (discarded_shift - 1U);
                if (discarded > halfway || (discarded == halfway && (significand & UINT64_C(1)) != UINT64_C(0))) {
                    significand++;
                    if (significand == (UINT64_C(1) << 53U)) {
                        significand >>= 1U;
                        exponent++;
                    }
                }
            }

            bits |= ((uint64_t) (exponent + 1023U) << 52U)
                | (significand & UINT64_C(0x000fffffffffffff));
            double result = 0.0;
            memcpy(&result, &bits, sizeof(result));
            return result;
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
        #if defined(_WIN32)
            LARGE_INTEGER frequency;
            LARGE_INTEGER counter;
            if (QueryPerformanceFrequency(&frequency) != 0
                && frequency.QuadPart > 0
                && QueryPerformanceCounter(&counter) != 0) {
                return (long long) ((((long double) counter.QuadPart) * 1000000000.0L) / (long double) frequency.QuadPart);
            }
            return (long long) GetTickCount64() * 1000000LL;
        #elif defined(CLOCK_MONOTONIC)
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
            if (strcmp(key, "javan.executable") == 0) {
                return javan_runtime_executable_path[0] == '\\0' ? NULL : javan_runtime_executable_path;
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

        void* javan_objects_require_non_null_else(void* value, void* fallback) {
            if (value != NULL) {
                return value;
            }
            return javan_objects_require_non_null(fallback);
        }

        void* javan_objects_to_string_default(void* value, void* default_value) {
            if (value == NULL) {
                return default_value;
            }
            return javan_printable_object_string(value);
        }
        """;

    private RuntimeSourceCoreSection() {
    }

    static String main() {
        return MAIN;
    }
}
