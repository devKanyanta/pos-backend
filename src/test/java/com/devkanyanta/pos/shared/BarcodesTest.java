package com.devkanyanta.pos.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.util.Barcodes;

class BarcodesTest {

    @Test
    void normalizeTrimsAndUpperCases() {
        assertThat(Barcodes.normalize("  6001234567890 ")).isEqualTo("6001234567890");
        assertThat(Barcodes.normalize("abc-123")).isEqualTo("ABC-123");
    }

    @Test
    void blankMeansNoBarcode() {
        assertThat(Barcodes.normalize(null)).isNull();
        assertThat(Barcodes.normalize("")).isNull();
        assertThat(Barcodes.normalize("   ")).isNull();
    }

    @Test
    void rejectsValuesThatCannotBeStored() {
        assertThatThrownBy(() -> Barcodes.normalize("12")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Barcodes.normalize("123 456")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Barcodes.normalize("12#456")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Barcodes.normalize("6.00123E+12 ")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid barcode");
        assertThatThrownBy(() -> Barcodes.normalize("1".repeat(65))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsCommonRetailFormats() {
        for (String code : new String[] {"6001234567890", "12345670", "012345678905", "04210000526", "ITEM_001/A+2", "123"}) {
            assertThat(Barcodes.normalize(code)).isEqualTo(code);
        }
    }

    @Test
    void canonicalIsLenientForLookups() {
        assertThat(Barcodes.canonical(" ab 12 ")).isEqualTo("AB 12");
        assertThat(Barcodes.canonical(" ")).isNull();
    }

    @Test
    void upcAndEanFormsAreEquivalent() {
        assertThat(Barcodes.equivalents("012345678905")).containsExactly("012345678905", "0012345678905");
        assertThat(Barcodes.equivalents("0012345678905")).containsExactly("0012345678905", "012345678905");
        assertThat(Barcodes.equivalents("6001234567890")).containsExactly("6001234567890");
        assertThat(Barcodes.equivalents("ABC123")).containsExactly("ABC123");
    }
}
