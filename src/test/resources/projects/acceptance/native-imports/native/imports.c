#include "javan_runtime.h"

int imported_target_bias(void);

int imported_adjust(int value) {
    return value * imported_target_bias();
}

int imported_mutate(JavanNativeImportedByteArray values) {
    values.data[1] = 11;
    return values.length;
}
