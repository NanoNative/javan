#include <stdio.h>
#include <stdint.h>
#include <string.h>

#include ".javan/dist/bindings/c/native-library.h"

int main(void) {
    printf("%d\n", javan_export_com_acme_Math_add_int_int(4, 6));
    int try_add = 0;
    JavanResult try_add_result = javan_try_com_acme_Math_add_int_int(4, 6, &try_add);
    if (try_add_result.ok != 1 || try_add != 10 || try_add_result.message != NULL) {
        printf("try-add-failed\n");
        return 1;
    }
    printf("try-add:%d:%d\n", try_add_result.ok, try_add);
    javan_result_free(&try_add_result);

    char* greeting = javan_export_com_acme_Text_greet_string("Yuna");
    printf("%s\n", greeting);
    javan_free(greeting);
    char* null_greeting = javan_export_com_acme_Text_greet_string(NULL);
    printf("null-greeting:%s\n", null_greeting);
    javan_free(null_greeting);

    int8_t data[4] = {3, 1, 4, 1};
    JavanByteArray input = {data, 4};
    JavanByteArray output = javan_export_com_acme_Bytes_duplicate_bytes(input);
    printf("%d:%d:%d\n", output.length, output.data[0], output.data[2]);
    javan_free(output.data);
    JavanByteArray empty = {NULL, 0};
    JavanByteArray empty_output = javan_export_com_acme_Bytes_duplicate_bytes(empty);
    printf("empty-bytes:%d:%s\n", empty_output.length, javan_last_error() == NULL ? "clean" : "dirty");
    javan_free(empty_output.data);
    JavanByteArray merged_output = javan_export_com_acme_Bytes_merge_bytes_bytes(empty, input);
    printf("merged-bytes:%d:%d:%d\n", merged_output.length, merged_output.data[0], merged_output.data[3]);
    javan_free(merged_output.data);

    char retained_name[5] = {'Y', 'u', 'n', 'a', 0};
    javan_export_com_acme_Store_rememberString_string(retained_name);
    retained_name[0] = 'L';
    char* retained = javan_export_com_acme_Store_lastString_void();
    printf("retained:%s\n", retained);
    javan_free(retained);

    int8_t retained_data[3] = {3, 1, 4};
    JavanByteArray retained_input = {retained_data, 3};
    javan_export_com_acme_Store_rememberBytes_bytes(retained_input);
    retained_data[1] = 9;
    JavanByteArray retained_output = javan_export_com_acme_Store_lastBytes_void();
    printf("retained-bytes:%d:%d:%d\n", retained_output.data[0], retained_output.data[1], retained_output.data[2]);
    javan_free(retained_output.data);
    javan_export_com_acme_Store_clear_void();

    javan_clear_error();
    int failed = javan_export_com_acme_Failures_failInt_void();
    const char* error = javan_last_error();
    if (error == NULL || strstr(error, "negative array length") == NULL) {
        printf("last-error-missing\n");
        return 1;
    }
    if (strcmp(javan_last_error_code(), "JAVAN-RUNTIME-PANIC") != 0
            || strcmp(javan_last_error_summary(), "runtime helper failure") != 0
            || strcmp(javan_last_error_class(), "com.acme.Failures") != 0
            || strstr(javan_last_error_method(), "failInt()I") == NULL
            || strcmp(javan_last_error_file(), "Failures.java") != 0
            || javan_last_error_line() != 8
            || javan_last_error_bytecode_offset() < 0
            || strstr(javan_last_error_source_line(), "new int[-1]") == NULL
            || strstr(javan_last_error_detail(), "negative array length") == NULL) {
        printf("last-error-structured-missing\n");
        return 1;
    }
    printf("last-error:%d:negative array length\n", failed);
    int try_failed = 42;
    JavanResult fail_result = javan_try_com_acme_Failures_failInt_void(&try_failed);
    if (fail_result.ok != 0
            || try_failed != 0
            || fail_result.code == NULL
            || strcmp(fail_result.code, "JAVAN-RUNTIME-PANIC") != 0
            || fail_result.detail == NULL
            || strstr(fail_result.detail, "negative array length") == NULL
            || fail_result.line != 8
            || fail_result.bytecode_offset < 0) {
        printf("result-error-missing\n");
        return 1;
    }
    javan_clear_error();
    printf("result-error:%s\n", fail_result.detail);
    javan_result_free(&fail_result);
    javan_clear_error();
    if (javan_last_error() != NULL
            || javan_last_error_code() != NULL
            || javan_last_error_line() != -1
            || javan_last_error_bytecode_offset() != -1) {
        printf("last-error-dirty\n");
        return 1;
    }
    JavanByteArray negative_length = {data, -1};
    JavanByteArray failed_bytes = javan_export_com_acme_Bytes_merge_bytes_bytes(input, negative_length);
    if (failed_bytes.data != NULL
            || failed_bytes.length != 0
            || javan_last_error() == NULL
            || strstr(javan_last_error(), "negative byte array length") == NULL
            || strcmp(javan_last_error_code(), "JAVAN-RUNTIME-PANIC") != 0
            || strstr(javan_last_error_detail(), "negative byte array length") == NULL) {
        printf("byte-error-missing\n");
        return 1;
    }
    printf("byte-error:negative byte array length\n");
    JavanByteArray try_failed_bytes = {data, 9};
    JavanResult byte_result = javan_try_com_acme_Bytes_merge_bytes_bytes(input, negative_length, &try_failed_bytes);
    if (byte_result.ok != 0
            || try_failed_bytes.data != NULL
            || try_failed_bytes.length != 0
            || byte_result.detail == NULL
            || strstr(byte_result.detail, "negative byte array length") == NULL) {
        printf("byte-result-error-missing\n");
        return 1;
    }
    printf("byte-result-error:%s\n", byte_result.detail);
    javan_result_free(&byte_result);
    javan_clear_error();

    for (int index = 0; index < 256; index++) {
        char* loop_greeting = javan_export_com_acme_Text_greet_string("Loop");
        javan_free(loop_greeting);
        JavanByteArray loop_output = javan_export_com_acme_Bytes_duplicate_bytes(input);
        javan_free(loop_output.data);
    }
    javan_free(NULL);

    return 0;
}
