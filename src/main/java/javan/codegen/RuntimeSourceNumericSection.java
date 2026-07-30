package javan.codegen;

final class RuntimeSourceNumericSection {
    private static final String MAIN = """
        #define JAVAN_DOUBLE_PARSE_NULL 1
        #define JAVAN_DOUBLE_PARSE_MALFORMED 2
        #define JAVAN_DOUBLE_PARSE_EMPTY 3
        #define JAVAN_DOUBLE_DECIMAL_DIGITS 1100
        #define JAVAN_DOUBLE_HEX_DIGITS 16
        #define JAVAN_BIG_LIMBS 128
        #define JAVAN_EXPONENT_CLAMP 10000000000LL

        typedef struct {
            uint32_t limbs[JAVAN_BIG_LIMBS];
            int length;
        } JavanBigUnsigned;

        static void javan_big_zero(JavanBigUnsigned* value) {
            memset(value, 0, sizeof(*value));
        }

        static void javan_big_normalize(JavanBigUnsigned* value) {
            while (value->length > 0 && value->limbs[value->length - 1] == 0U) {
                value->length--;
            }
        }

        static void javan_big_from_uint32(JavanBigUnsigned* value, uint32_t initial) {
            javan_big_zero(value);
            if (initial != 0U) {
                value->limbs[0] = initial;
                value->length = 1;
            }
        }

        static void javan_big_require_limb(int index) {
            if (index < 0 || index >= JAVAN_BIG_LIMBS) {
                javan_panic("binary64 parser fixed integer capacity exceeded");
            }
        }

        static void javan_big_multiply_add(
            JavanBigUnsigned* value,
            uint32_t multiplier,
            uint32_t addend
        ) {
            uint64_t carry = addend;
            for (int index = 0; index < value->length; index++) {
                const uint64_t product = (uint64_t) value->limbs[index]
                    * (uint64_t) multiplier + carry;
                value->limbs[index] = (uint32_t) product;
                carry = product >> 32;
            }
            while (carry != 0U) {
                javan_big_require_limb(value->length);
                value->limbs[value->length] = (uint32_t) carry;
                value->length++;
                carry >>= 32;
            }
        }

        static void javan_big_multiply_power_of_five(
            JavanBigUnsigned* value,
            int exponent
        ) {
            for (int index = 0; index < exponent; index++) {
                javan_big_multiply_add(value, 5U, 0U);
            }
        }

        static int javan_uint32_bit_length(uint32_t value) {
            int result = 0;
            while (value != 0U) {
                result++;
                value >>= 1;
            }
            return result;
        }

        static int javan_big_bit_length(const JavanBigUnsigned* value) {
            if (value->length == 0) {
                return 0;
            }
            return (value->length - 1) * 32
                + javan_uint32_bit_length(value->limbs[value->length - 1]);
        }

        static int javan_big_compare(
            const JavanBigUnsigned* left,
            const JavanBigUnsigned* right
        ) {
            if (left->length != right->length) {
                return left->length < right->length ? -1 : 1;
            }
            for (int index = left->length - 1; index >= 0; index--) {
                if (left->limbs[index] != right->limbs[index]) {
                    return left->limbs[index] < right->limbs[index] ? -1 : 1;
                }
            }
            return 0;
        }

        static void javan_big_left_shift(
            const JavanBigUnsigned* source,
            int shift,
            JavanBigUnsigned* target
        ) {
            if (shift < 0) {
                javan_panic("binary64 parser received a negative left shift");
            }
            javan_big_zero(target);
            if (source->length == 0) {
                return;
            }
            const int word_shift = shift / 32;
            const int bit_shift = shift % 32;
            for (int index = 0; index < source->length; index++) {
                const int target_index = index + word_shift;
                javan_big_require_limb(target_index);
                const uint64_t shifted = (uint64_t) source->limbs[index] << bit_shift;
                target->limbs[target_index] |= (uint32_t) shifted;
                if ((shifted >> 32) != 0U) {
                    javan_big_require_limb(target_index + 1);
                    target->limbs[target_index + 1] |= (uint32_t) (shifted >> 32);
                }
            }
            target->length = source->length + word_shift + (bit_shift == 0 ? 0 : 1);
            if (target->length > JAVAN_BIG_LIMBS) {
                target->length = JAVAN_BIG_LIMBS;
            }
            javan_big_normalize(target);
        }

        static void javan_big_right_shift_one(JavanBigUnsigned* value) {
            uint32_t carry = 0U;
            for (int index = value->length - 1; index >= 0; index--) {
                const uint32_t next_carry = value->limbs[index] & 1U;
                value->limbs[index] = (value->limbs[index] >> 1) | (carry << 31);
                carry = next_carry;
            }
            javan_big_normalize(value);
        }

        static void javan_big_subtract(
            JavanBigUnsigned* left,
            const JavanBigUnsigned* right
        ) {
            uint64_t borrow = 0U;
            for (int index = 0; index < left->length; index++) {
                const uint64_t subtrahend = (index < right->length
                    ? (uint64_t) right->limbs[index]
                    : 0U) + borrow;
                const uint64_t current = left->limbs[index];
                left->limbs[index] = (uint32_t) (current - subtrahend);
                borrow = current < subtrahend ? 1U : 0U;
            }
            if (borrow != 0U) {
                javan_panic("binary64 parser big integer subtraction underflow");
            }
            javan_big_normalize(left);
        }

        static int javan_big_compare_twice(
            const JavanBigUnsigned* left,
            const JavanBigUnsigned* right
        ) {
            JavanBigUnsigned half = *right;
            const int right_is_odd = half.length > 0 && (half.limbs[0] & 1U) != 0U;
            javan_big_right_shift_one(&half);
            const int comparison = javan_big_compare(left, &half);
            if (comparison != 0) {
                return comparison;
            }
            return right_is_odd ? -1 : 0;
        }

        static int javan_ratio_binary_exponent(
            const JavanBigUnsigned* numerator,
            const JavanBigUnsigned* denominator
        ) {
            int exponent = javan_big_bit_length(numerator)
                - javan_big_bit_length(denominator);
            JavanBigUnsigned scaled;
            int comparison;
            if (exponent >= 0) {
                javan_big_left_shift(denominator, exponent, &scaled);
                comparison = javan_big_compare(numerator, &scaled);
            } else {
                javan_big_left_shift(numerator, -exponent, &scaled);
                comparison = javan_big_compare(&scaled, denominator);
            }
            if (comparison < 0) {
                exponent--;
            }
            return exponent;
        }

        static uint64_t javan_ratio_rounded_quotient(
            const JavanBigUnsigned* numerator,
            const JavanBigUnsigned* denominator,
            int binary_shift
        ) {
            JavanBigUnsigned dividend;
            JavanBigUnsigned divisor;
            if (binary_shift >= 0) {
                javan_big_left_shift(numerator, binary_shift, &dividend);
                divisor = *denominator;
            } else {
                dividend = *numerator;
                javan_big_left_shift(denominator, -binary_shift, &divisor);
            }

            int quotient_shift = javan_big_bit_length(&dividend)
                - javan_big_bit_length(&divisor);
            uint64_t quotient = 0U;
            if (quotient_shift >= 0) {
                if (quotient_shift >= 64) {
                    javan_panic("binary64 parser quotient exceeded 64 bits");
                }
                JavanBigUnsigned shifted_divisor;
                javan_big_left_shift(&divisor, quotient_shift, &shifted_divisor);
                for (int bit = quotient_shift; bit >= 0; bit--) {
                    if (javan_big_compare(&dividend, &shifted_divisor) >= 0) {
                        javan_big_subtract(&dividend, &shifted_divisor);
                        quotient |= UINT64_C(1) << bit;
                    }
                    javan_big_right_shift_one(&shifted_divisor);
                }
            }
            const int halfway = javan_big_compare_twice(&dividend, &divisor);
            if (halfway > 0 || (halfway == 0 && (quotient & 1U) != 0U)) {
                quotient++;
            }
            return quotient;
        }

        static double javan_double_from_bits(uint64_t bits) {
            double result = 0.0;
            memcpy(&result, &bits, sizeof(result));
            return result;
        }

        static double javan_binary64_from_ratio(
            const JavanBigUnsigned* numerator,
            const JavanBigUnsigned* denominator,
            long long binary_shift,
            int negative
        ) {
            if (numerator->length == 0) {
                return javan_double_from_bits(negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000));
            }
            const long long exponent = (long long) javan_ratio_binary_exponent(
                numerator,
                denominator
            ) + binary_shift;
            if (exponent > 1023LL) {
                return javan_double_from_bits((negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000))
                    | UINT64_C(0x7ff0000000000000));
            }
            if (exponent < -1075LL) {
                return javan_double_from_bits(negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000));
            }

            long long rounded_exponent = exponent;
            const long long quantum_exponent = exponent >= -1022LL
                ? exponent - 52LL
                : -1074LL;
            const long long quotient_shift = binary_shift - quantum_exponent;
            if (quotient_shift < INT_MIN || quotient_shift > INT_MAX) {
                javan_panic("binary64 parser scaling shift exceeded native integer range");
            }
            uint64_t significand = javan_ratio_rounded_quotient(
                numerator,
                denominator,
                (int) quotient_shift
            );
            uint64_t bits;
            if (exponent >= -1022LL) {
                if (significand >= UINT64_C(0x0020000000000000)) {
                    significand >>= 1;
                    rounded_exponent++;
                }
                if (rounded_exponent > 1023LL) {
                    bits = UINT64_C(0x7ff0000000000000);
                } else {
                    bits = ((uint64_t) (rounded_exponent + 1023LL) << 52)
                        | (significand - UINT64_C(0x0010000000000000));
                }
            } else {
                bits = significand >= UINT64_C(0x0010000000000000)
                    ? UINT64_C(0x0010000000000000)
                    : significand;
            }
            if (negative != 0) {
                bits |= UINT64_C(0x8000000000000000);
            }
            return javan_double_from_bits(bits);
        }

        static long long javan_saturating_add(long long left, long long right) {
            if (right > 0LL && left > JAVAN_EXPONENT_CLAMP - right) {
                return JAVAN_EXPONENT_CLAMP;
            }
            if (right < 0LL && left < -JAVAN_EXPONENT_CLAMP - right) {
                return -JAVAN_EXPONENT_CLAMP;
            }
            const long long result = left + right;
            if (result > JAVAN_EXPONENT_CLAMP) {
                return JAVAN_EXPONENT_CLAMP;
            }
            if (result < -JAVAN_EXPONENT_CLAMP) {
                return -JAVAN_EXPONENT_CLAMP;
            }
            return result;
        }

        static long long javan_saturating_increment(long long value) {
            return value >= JAVAN_EXPONENT_CLAMP
                ? JAVAN_EXPONENT_CLAMP
                : value + 1LL;
        }

        static int javan_range_equals(
            const unsigned char* start,
            const unsigned char* end,
            const char* expected
        ) {
            const unsigned long length = (unsigned long) (end - start);
            return strlen(expected) == length
                && memcmp(start, expected, length) == 0;
        }

        static int javan_parse_exponent(
            const unsigned char* cursor,
            const unsigned char* end,
            long long* exponent
        ) {
            int negative = 0;
            if (cursor < end && (*cursor == '+' || *cursor == '-')) {
                negative = *cursor == '-';
                cursor++;
            }
            if (cursor == end || *cursor < '0' || *cursor > '9') {
                return 0;
            }
            long long magnitude = 0LL;
            while (cursor < end) {
                if (*cursor < '0' || *cursor > '9') {
                    return 0;
                }
                const int digit = *cursor - '0';
                if (magnitude > (JAVAN_EXPONENT_CLAMP - digit) / 10LL) {
                    magnitude = JAVAN_EXPONENT_CLAMP;
                } else {
                    magnitude = magnitude * 10LL + digit;
                }
                cursor++;
            }
            *exponent = negative ? -magnitude : magnitude;
            return 1;
        }

        static int javan_parse_decimal_double(
            const unsigned char* start,
            const unsigned char* end,
            int negative,
            double* parsed
        ) {
            unsigned char digits[JAVAN_DOUBLE_DECIMAL_DIGITS + 1];
            long long digits_before_dot = 0LL;
            long long leading_zeros = 0LL;
            long long significant_positions = 0LL;
            long long last_non_zero = 0LL;
            int saw_digit = 0;
            int saw_dot = 0;
            int saw_non_zero = 0;
            const unsigned char* cursor = start;
            while (cursor < end && *cursor != 'e' && *cursor != 'E') {
                if (*cursor >= '0' && *cursor <= '9') {
                    const unsigned char digit = (unsigned char) (*cursor - '0');
                    saw_digit = 1;
                    if (saw_dot == 0) {
                        digits_before_dot = javan_saturating_increment(digits_before_dot);
                    }
                    if (saw_non_zero == 0) {
                        if (digit == 0U) {
                            leading_zeros = javan_saturating_increment(leading_zeros);
                        } else {
                            saw_non_zero = 1;
                        }
                    }
                    if (saw_non_zero != 0) {
                        significant_positions = javan_saturating_increment(
                            significant_positions
                        );
                        if (significant_positions <= JAVAN_DOUBLE_DECIMAL_DIGITS) {
                            digits[(int) significant_positions - 1] = digit;
                        }
                        if (digit != 0U) {
                            last_non_zero = significant_positions;
                        }
                    }
                } else if (*cursor == '.' && saw_dot == 0) {
                    saw_dot = 1;
                } else {
                    return JAVAN_DOUBLE_PARSE_MALFORMED;
                }
                cursor++;
            }
            if (saw_digit == 0) {
                return JAVAN_DOUBLE_PARSE_MALFORMED;
            }

            long long explicit_exponent = 0LL;
            if (cursor < end) {
                cursor++;
                if (javan_parse_exponent(cursor, end, &explicit_exponent) == 0) {
                    return JAVAN_DOUBLE_PARSE_MALFORMED;
                }
            }
            if (last_non_zero == 0LL) {
                *parsed = javan_double_from_bits(negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000));
                return 0;
            }

            int retained_digits;
            if (last_non_zero > JAVAN_DOUBLE_DECIMAL_DIGITS) {
                retained_digits = JAVAN_DOUBLE_DECIMAL_DIGITS + 1;
                digits[JAVAN_DOUBLE_DECIMAL_DIGITS] = 1U;
            } else {
                retained_digits = (int) last_non_zero;
            }
            const long long decimal_exponent = javan_saturating_add(
                javan_saturating_add(digits_before_dot, -leading_zeros),
                explicit_exponent
            );
            if (decimal_exponent > 309LL) {
                *parsed = javan_double_from_bits((negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000))
                    | UINT64_C(0x7ff0000000000000));
                return 0;
            }
            if (decimal_exponent < -325LL) {
                *parsed = javan_double_from_bits(negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000));
                return 0;
            }

            JavanBigUnsigned numerator;
            JavanBigUnsigned denominator;
            javan_big_from_uint32(&numerator, 0U);
            for (int index = 0; index < retained_digits; index++) {
                javan_big_multiply_add(&numerator, 10U, digits[index]);
            }
            javan_big_from_uint32(&denominator, 1U);
            const int scale = (int) (decimal_exponent - retained_digits);
            if (scale >= 0) {
                javan_big_multiply_power_of_five(&numerator, scale);
            } else {
                javan_big_multiply_power_of_five(&denominator, -scale);
            }
            *parsed = javan_binary64_from_ratio(
                &numerator,
                &denominator,
                scale,
                negative
            );
            return 0;
        }

        static int javan_hex_digit(unsigned char value) {
            if (value >= '0' && value <= '9') {
                return value - '0';
            }
            if (value >= 'a' && value <= 'f') {
                return value - 'a' + 10;
            }
            if (value >= 'A' && value <= 'F') {
                return value - 'A' + 10;
            }
            return -1;
        }

        static int javan_parse_hex_double(
            const unsigned char* start,
            const unsigned char* end,
            int negative,
            double* parsed
        ) {
            if (end - start < 3 || start[0] != '0'
                || (start[1] != 'x' && start[1] != 'X')) {
                return JAVAN_DOUBLE_PARSE_MALFORMED;
            }
            unsigned char digits[JAVAN_DOUBLE_HEX_DIGITS + 1];
            long long digits_before_dot = 0LL;
            long long leading_zeros = 0LL;
            long long significant_positions = 0LL;
            long long last_non_zero = 0LL;
            int saw_digit = 0;
            int saw_dot = 0;
            int saw_non_zero = 0;
            const unsigned char* cursor = start + 2;
            while (cursor < end && *cursor != 'p' && *cursor != 'P') {
                const int digit = javan_hex_digit(*cursor);
                if (digit >= 0) {
                    saw_digit = 1;
                    if (saw_dot == 0) {
                        digits_before_dot = javan_saturating_increment(digits_before_dot);
                    }
                    if (saw_non_zero == 0) {
                        if (digit == 0) {
                            leading_zeros = javan_saturating_increment(leading_zeros);
                        } else {
                            saw_non_zero = 1;
                        }
                    }
                    if (saw_non_zero != 0) {
                        significant_positions = javan_saturating_increment(
                            significant_positions
                        );
                        if (significant_positions <= JAVAN_DOUBLE_HEX_DIGITS) {
                            digits[(int) significant_positions - 1] = (unsigned char) digit;
                        }
                        if (digit != 0) {
                            last_non_zero = significant_positions;
                        }
                    }
                } else if (*cursor == '.' && saw_dot == 0) {
                    saw_dot = 1;
                } else {
                    return JAVAN_DOUBLE_PARSE_MALFORMED;
                }
                cursor++;
            }
            if (saw_digit == 0 || cursor == end) {
                return JAVAN_DOUBLE_PARSE_MALFORMED;
            }

            long long explicit_exponent = 0LL;
            cursor++;
            if (javan_parse_exponent(cursor, end, &explicit_exponent) == 0) {
                return JAVAN_DOUBLE_PARSE_MALFORMED;
            }
            if (last_non_zero == 0LL) {
                *parsed = javan_double_from_bits(negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000));
                return 0;
            }

            int retained_digits;
            if (last_non_zero > JAVAN_DOUBLE_HEX_DIGITS) {
                retained_digits = JAVAN_DOUBLE_HEX_DIGITS + 1;
                digits[JAVAN_DOUBLE_HEX_DIGITS] = 1U;
            } else {
                retained_digits = (int) last_non_zero;
            }
            JavanBigUnsigned numerator;
            JavanBigUnsigned denominator;
            javan_big_from_uint32(&numerator, 0U);
            for (int index = 0; index < retained_digits; index++) {
                javan_big_multiply_add(&numerator, 16U, digits[index]);
            }
            javan_big_from_uint32(&denominator, 1U);
            const long long position = javan_saturating_add(
                digits_before_dot,
                -leading_zeros
            );
            long long binary_shift = javan_saturating_add(
                explicit_exponent,
                (position - retained_digits) * 4LL
            );
            *parsed = javan_binary64_from_ratio(
                &numerator,
                &denominator,
                binary_shift,
                negative
            );
            return 0;
        }

        static int javan_double_parse(const char* value, double* parsed) {
            if (value == NULL) {
                return JAVAN_DOUBLE_PARSE_NULL;
            }
            const unsigned char* start = (const unsigned char*) value;
            const unsigned char* end = start + strlen(value);
            while (start < end && *start <= 0x20U) {
                start++;
            }
            while (end > start && end[-1] <= 0x20U) {
                end--;
            }
            if (start == end) {
                return JAVAN_DOUBLE_PARSE_EMPTY;
            }

            int negative = 0;
            if (*start == '+' || *start == '-') {
                negative = *start == '-';
                start++;
                if (start == end) {
                    return JAVAN_DOUBLE_PARSE_MALFORMED;
                }
            }
            if (javan_range_equals(start, end, "NaN")) {
                *parsed = javan_double_from_bits(UINT64_C(0x7ff8000000000000));
                return 0;
            }
            if (javan_range_equals(start, end, "Infinity")) {
                *parsed = javan_double_from_bits((negative == 0
                    ? UINT64_C(0)
                    : UINT64_C(0x8000000000000000))
                    | UINT64_C(0x7ff0000000000000));
                return 0;
            }

            const unsigned char suffix = end[-1];
            if (suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D') {
                end--;
                if (start == end) {
                    return JAVAN_DOUBLE_PARSE_MALFORMED;
                }
            }
            if (end - start >= 2 && start[0] == '0'
                && (start[1] == 'x' || start[1] == 'X')) {
                return javan_parse_hex_double(start, end, negative, parsed);
            }
            return javan_parse_decimal_double(start, end, negative, parsed);
        }

        int javan_double_parse_status(const char* value) {
            double parsed = 0.0;
            return javan_double_parse(value, &parsed);
        }

        double javan_double_parse_value(const char* value) {
            double parsed = 0.0;
            if (javan_double_parse(value, &parsed) != 0) {
                javan_panic("double parse value requested for invalid input");
            }
            return parsed;
        }

        void* javan_double_parse_message(const char* value, int status) {
            if (status == JAVAN_DOUBLE_PARSE_NULL) {
                return (void*) "Cannot invoke \\"String.length()\\" because \\"in\\" is null";
            }
            if (status == JAVAN_DOUBLE_PARSE_EMPTY) {
                return (void*) "empty String";
            }
            return javan_decimal_parse_message(value, status);
        }

        """;

    private RuntimeSourceNumericSection() {
    }

    static String main() {
        return MAIN;
    }
}
