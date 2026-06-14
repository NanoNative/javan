#include <stdio.h>
#include <stdint.h>

#include ".javan/dist/bindings/c/native-library.h"

int main(void) {
    printf("%d\n", javan_export_com_acme_Math_add_int_int(4, 6));

    char* greeting = javan_export_com_acme_Text_greet_string("Yuna");
    printf("%s\n", greeting);
    javan_free(greeting);

    int8_t data[4] = {3, 1, 4, 1};
    JavanByteArray input = {data, 4};
    JavanByteArray output = javan_export_com_acme_Bytes_duplicate_bytes(input);
    printf("%d:%d:%d\n", output.length, output.data[0], output.data[2]);
    javan_free(output.data);

    for (int index = 0; index < 256; index++) {
        char* loop_greeting = javan_export_com_acme_Text_greet_string("Loop");
        javan_free(loop_greeting);
        JavanByteArray loop_output = javan_export_com_acme_Bytes_duplicate_bytes(input);
        javan_free(loop_output.data);
    }
    javan_free(NULL);

    return 0;
}
