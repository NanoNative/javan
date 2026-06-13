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

        void javan_println(const char* value);
        void javan_println_int(int value);
        void javan_println_long(long long value);
        void javan_println_float(float value);
        void javan_println_double(double value);
        void javan_println_bool(int value);
        int javan_math_abs_int(int value);
        long long javan_math_abs_long(long long value);
        int javan_math_min_int(int left, int right);
        long long javan_math_min_long(long long left, long long right);
        int javan_math_max_int(int left, int right);
        long long javan_math_max_long(long long left, long long right);
        long long javan_system_nano_time(void);
        long long javan_system_current_time_millis(void);
        void javan_system_arraycopy(void* source, int source_position, void* target, int target_position, int length);
        void* javan_objects_require_non_null(void* value);
        typedef struct {
            signed char* data;
            int length;
        } JavanByteArray;
        void* javan_alloc(unsigned long size);
        void javan_free(void* value);
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
        void* javan_string_array_from_args(int argc, char** argv);
        int javan_string_length(const char* value);
        int javan_string_is_empty(const char* value);
        int javan_string_char_at(const char* value, int index);
        int javan_string_equals(const char* left, const char* right);
        void* javan_string_value_of_int(int value);
        void* javan_string_value_of_long(long long value);
        void* javan_string_value_of_float(float value);
        void* javan_string_value_of_double(double value);
        void* javan_string_value_of_bool(int value);
        void* javan_string_value_of_char(int value);
        void* javan_string_concat(const char* recipe, int argc, const char** values);
        char* javan_string_export(const char* value);
        int javan_float_compare(float left, float right, int nan_value);
        int javan_double_compare(double left, double right, int nan_value);
        void javan_panic(const char* value);

        #endif
        """;

    private static final String SOURCE = """
        #include "javan_runtime.h"

        #include <limits.h>
        #include <math.h>
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        #include <sys/time.h>
        #include <time.h>

        void javan_println(const char* value) {
            puts(value == NULL ? "" : value);
        }

        void javan_println_int(int value) {
            printf("%d\\n", value);
        }

        void javan_println_long(long long value) {
            printf("%lld\\n", value);
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

        void javan_println_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            puts(buffer);
        }

        void javan_println_bool(int value) {
            puts(value == 0 ? "false" : "true");
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

        void* javan_objects_require_non_null(void* value) {
            if (value == NULL) {
                javan_panic("null object");
            }
            return value;
        }

        void* javan_alloc(unsigned long size) {
            void* value = calloc(1, size);
            if (value == NULL) {
                javan_panic("out of memory");
            }
            return value;
        }

        void javan_free(void* value) {
            free(value);
        }

        typedef struct {
            int length;
            int element_size;
            int kind;
            int reserved;
        } javan_array_header;

        #define JAVAN_ARRAY_KIND_OBJECT 1
        #define JAVAN_ARRAY_KIND_INT 2
        #define JAVAN_ARRAY_KIND_LONG 3
        #define JAVAN_ARRAY_KIND_FLOAT 4
        #define JAVAN_ARRAY_KIND_DOUBLE 5
        #define JAVAN_ARRAY_KIND_BYTE 6
        #define JAVAN_ARRAY_KIND_BOOLEAN 7
        #define JAVAN_ARRAY_KIND_SHORT 8
        #define JAVAN_ARRAY_KIND_CHAR 9

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

        static void javan_array_init(javan_array_header* array, int length, int element_size, int kind) {
            array->length = length;
            array->element_size = element_size;
            array->kind = kind;
            array->reserved = 0;
        }

        void* javan_object_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_object_array) + ((unsigned long) length * sizeof(void*));
            javan_object_array* array = (javan_object_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(void*), JAVAN_ARRAY_KIND_OBJECT);
            return array;
        }

        void* javan_int_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_int_array) + ((unsigned long) length * sizeof(int));
            javan_int_array* array = (javan_int_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(int), JAVAN_ARRAY_KIND_INT);
            return array;
        }

        void* javan_long_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_long_array) + ((unsigned long) length * sizeof(long long));
            javan_long_array* array = (javan_long_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(long long), JAVAN_ARRAY_KIND_LONG);
            return array;
        }

        void* javan_float_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_float_array) + ((unsigned long) length * sizeof(float));
            javan_float_array* array = (javan_float_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(float), JAVAN_ARRAY_KIND_FLOAT);
            return array;
        }

        void* javan_double_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_double_array) + ((unsigned long) length * sizeof(double));
            javan_double_array* array = (javan_double_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(double), JAVAN_ARRAY_KIND_DOUBLE);
            return array;
        }

        void* javan_byte_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_byte_array) + ((unsigned long) length * sizeof(signed char));
            javan_byte_array* array = (javan_byte_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(signed char), JAVAN_ARRAY_KIND_BYTE);
            return array;
        }

        void* javan_boolean_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_byte_array) + ((unsigned long) length * sizeof(signed char));
            javan_byte_array* array = (javan_byte_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(signed char), JAVAN_ARRAY_KIND_BOOLEAN);
            return array;
        }

        void* javan_short_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_short_array) + ((unsigned long) length * sizeof(short));
            javan_short_array* array = (javan_short_array*) javan_alloc(size);
            javan_array_init((javan_array_header*) array, length, sizeof(short), JAVAN_ARRAY_KIND_SHORT);
            return array;
        }

        void* javan_char_array_new(int length) {
            if (length < 0) {
                javan_panic("negative array length");
            }
            unsigned long size = sizeof(javan_char_array) + ((unsigned long) length * sizeof(unsigned short));
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
                result.data = (signed char*) javan_alloc((unsigned long) values->length);
                memcpy(result.data, values->values, (unsigned long) values->length);
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
            javan_array_header* source = javan_array_checked(array);
            javan_array_kind_checked(source, expected_kind);
            void* result = allocate(new_length);
            javan_array_header* target = javan_array_checked(result);
            int copied = source->length < new_length ? source->length : new_length;
            if (copied > 0) {
                memcpy(
                    javan_array_values(target),
                    javan_array_values(source),
                    (unsigned long) copied * (unsigned long) source->element_size
                );
            }
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

        void* javan_string_array_from_args(int argc, char** argv) {
            int length = argc > 0 ? argc - 1 : 0;
            void* result = javan_object_array_new(length);
            for (int index = 0; index < length; index++) {
                javan_object_array_set(result, index, argv[index + 1]);
            }
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

        int javan_string_equals(const char* left, const char* right) {
            if (left == NULL || right == NULL) {
                return left == right;
            }
            return strcmp(left, right) == 0;
        }

        static void* javan_string_copy(const char* value) {
            const char* source = value == NULL ? "null" : value;
            unsigned long length = strlen(source);
            char* result = (char*) javan_alloc(length + 1);
            memcpy(result, source, length + 1);
            return result;
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
            unsigned long length = 0;
            int arg = 0;
            for (const unsigned char* cursor = (const unsigned char*) recipe; *cursor != '\\0'; cursor++) {
                if (*cursor == 1) {
                    if (arg >= argc) {
                        javan_panic("invalid string concat argument");
                    }
                    length += strlen(values[arg] == NULL ? "null" : values[arg]);
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
            char* result = (char*) javan_alloc(length + 1);
            char* out = result;
            arg = 0;
            for (const unsigned char* cursor = (const unsigned char*) recipe; *cursor != '\\0'; cursor++) {
                if (*cursor == 1) {
                    const char* value = values[arg] == NULL ? "null" : values[arg];
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
            return result;
        }

        char* javan_string_export(const char* value) {
            return (char*) javan_string_copy(value);
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
            fputs(value == NULL ? "javan panic" : value, stderr);
            fputc('\\n', stderr);
            exit(1);
        }
        """;

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
