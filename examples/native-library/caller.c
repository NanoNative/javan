#include <stdio.h>

#include ".javan/dist/bindings/c/native-library.h"

int main(void) {
    printf("%d\n", javan_export_com_acme_Math_add_int_int(4, 6));
    return 0;
}
