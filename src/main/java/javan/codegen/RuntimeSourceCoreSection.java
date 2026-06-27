package javan.codegen;

final class RuntimeSourceCoreSection {
    private static final String MAIN = """
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
        #include <windows.h>
        #include <process.h>
        #include <io.h>
        #include <sys/time.h>
        #else
        #include <arpa/inet.h>
        #include <netinet/in.h>
        #include <pthread.h>
        #include <sys/socket.h>
        #include <sys/wait.h>
        #include <unistd.h>
        #endif
        #include <sys/stat.h>
        #include <sys/time.h>
        #include <time.h>
        #if defined(_MSC_VER)
        #define JAVAN_THREAD_LOCAL __declspec(thread)
        #else
        #define JAVAN_THREAD_LOCAL _Thread_local
        #endif

        static char* javan_string_alloc(unsigned long size);
        static void* javan_string_copy(const char* value);
        static int javan_socket_native_close(int fd);
        static void javan_sleep_micros(unsigned long micros);
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

    private RuntimeSourceCoreSection() {
    }

    static String main() {
        return MAIN;
    }
}
