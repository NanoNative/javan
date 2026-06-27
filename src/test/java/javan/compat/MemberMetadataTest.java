package javan.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class MemberMetadataTest {
    @Test
    void syntheticReturnsTrueForSyntheticAccessFlag() {
        final MemberMetadata metadata = new MemberMetadata(0x1000, "m", "()V", List.of(), List.of());

        assertThat(metadata.synthetic()).isTrue();
    }

    @Test
    void syntheticReturnsTrueForSyntheticAttribute() {
        final MemberMetadata metadata = new MemberMetadata(0, "m", "()V", List.of("Synthetic"), List.of());

        assertThat(metadata.synthetic()).isTrue();
    }

    @Test
    void syntheticReturnsFalseWhenNoSyntheticMarkerExists() {
        final MemberMetadata metadata = new MemberMetadata(0, "m", "()V", List.of(), List.of());

        assertThat(metadata.synthetic()).isFalse();
    }

    @Test
    void deprecatedReturnsTrueWhenDeprecatedAttributeExists() {
        final MemberMetadata metadata = new MemberMetadata(0, "m", "()V", List.of("Deprecated"), List.of());

        assertThat(metadata.deprecated()).isTrue();
    }

    @Test
    void deprecatedReturnsFalseWhenDeprecatedAttributeIsAbsent() {
        final MemberMetadata metadata = new MemberMetadata(0, "m", "()V", List.of(), List.of());

        assertThat(metadata.deprecated()).isFalse();
    }
}
