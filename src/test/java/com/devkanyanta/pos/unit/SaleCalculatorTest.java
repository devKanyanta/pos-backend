package com.devkanyanta.pos.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.sale.SaleCalculator;
import com.devkanyanta.pos.sale.SaleCalculator.LineInput;
import com.devkanyanta.pos.shared.exception.BadRequestException;

class SaleCalculatorTest {

    private static LineInput line(String price, int qty, String discount, String vat) {
        return new LineInput("item", new BigDecimal(price), qty, discount == null ? null : new BigDecimal(discount), new BigDecimal(vat));
    }

    @Test
    void vatIsChargedOnTheDiscountedAmount() {
        SaleCalculator.Result result = SaleCalculator.calculate(List.of(
                line("15.00", 2, "2.00", "16"),
                line("10.00", 1, null, "0")), new BigDecimal("3.80"));

        assertThat(result.subtotal()).isEqualByComparingTo("38.00");
        assertThat(result.lines().get(0).saleDiscountShare()).isEqualByComparingTo("2.80");
        assertThat(result.lines().get(1).saleDiscountShare()).isEqualByComparingTo("1.00");
        assertThat(result.lines().get(0).taxable()).isEqualByComparingTo("25.20");
        assertThat(result.vat()).isEqualByComparingTo("4.03");
        assertThat(result.total()).isEqualByComparingTo("38.23");
        assertThat(result.total()).isEqualByComparingTo(result.subtotal().subtract(result.saleDiscount()).add(result.vat()));
    }

    @Test
    void discountAllocationAlwaysSumsToTheSaleDiscount() {
        SaleCalculator.Result result = SaleCalculator.calculate(List.of(
                line("3.33", 1, null, "16"), line("3.33", 1, null, "16"), line("3.34", 1, null, "16")),
                new BigDecimal("1.00"));

        BigDecimal allocated = result.lines().stream()
                .map(SaleCalculator.LineResult::saleDiscountShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo("1.00");
    }

    @Test
    void invalidDiscountsAreRejected() {
        assertThatThrownBy(() -> SaleCalculator.calculate(List.of(line("10.00", 1, "11.00", "16")), null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("exceeds");
        assertThatThrownBy(() -> SaleCalculator.calculate(List.of(line("10.00", 1, null, "16")), new BigDecimal("10.01")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("cannot exceed");
        assertThatThrownBy(() -> SaleCalculator.calculate(List.of(line("10.00", 1, null, "16")), new BigDecimal("-1")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SaleCalculator.calculate(List.of(line("10.00", 0, null, "16")), null))
                .isInstanceOf(BadRequestException.class);
    }
}
