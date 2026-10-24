package com.devkanyanta.pos.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.util.Money;

/**
 * Sale arithmetic. Prices are VAT exclusive.
 *
 * <pre>
 * gross         = unitPrice x quantity
 * lineTotal     = gross - lineDiscount
 * subtotal      = sum(lineTotal)
 * discountShare = saleDiscount allocated to lines pro rata to lineTotal (remainder on the last line)
 * taxable       = lineTotal - discountShare
 * vat           = taxable x vatRate / 100        (rounded half-up per line)
 * total         = subtotal - saleDiscount + sum(vat) = sum(taxable + vat)
 * </pre>
 */
public final class SaleCalculator {

    private SaleCalculator() {
    }

    public record LineInput(String label, BigDecimal unitPrice, int quantity, BigDecimal lineDiscount, BigDecimal vatRate) {
    }

    public record LineResult(
            BigDecimal gross,
            BigDecimal lineDiscount,
            BigDecimal lineTotal,
            BigDecimal saleDiscountShare,
            BigDecimal taxable,
            BigDecimal vat
    ) {
    }

    public record Result(List<LineResult> lines, BigDecimal subtotal, BigDecimal saleDiscount, BigDecimal vat, BigDecimal total) {
    }

    public static Result calculate(List<LineInput> inputs, BigDecimal saleDiscountRaw) {

        BigDecimal saleDiscount = Money.of(Money.nz(saleDiscountRaw));

        if (saleDiscount.signum() < 0) {
            throw new BadRequestException("Sale discount cannot be negative");
        }

        List<BigDecimal> grossAmounts = new ArrayList<>();
        List<BigDecimal> lineDiscounts = new ArrayList<>();
        List<BigDecimal> lineTotals = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;

        for (LineInput input : inputs) {

            if (input.quantity() <= 0) {
                throw new BadRequestException("Item quantity must be positive");
            }

            if (input.unitPrice() == null || input.unitPrice().signum() < 0) {
                throw new BadRequestException("Unit price cannot be negative for: " + input.label());
            }

            if (input.vatRate() != null && input.vatRate().signum() < 0) {
                throw new BadRequestException("VAT rate cannot be negative for: " + input.label());
            }

            BigDecimal gross = Money.of(input.unitPrice().multiply(BigDecimal.valueOf(input.quantity())));
            BigDecimal discount = Money.of(Money.nz(input.lineDiscount()));

            if (discount.signum() < 0) {
                throw new BadRequestException("Item discount cannot be negative for product: " + input.label());
            }

            if (discount.compareTo(gross) > 0) {
                throw new BadRequestException("Item discount exceeds the line amount for product: " + input.label());
            }

            BigDecimal lineTotal = gross.subtract(discount);
            grossAmounts.add(gross);
            lineDiscounts.add(discount);
            lineTotals.add(lineTotal);
            subtotal = subtotal.add(lineTotal);
        }

        if (saleDiscount.compareTo(subtotal) > 0) {
            throw new BadRequestException("Sale discount (" + saleDiscount + ") cannot exceed the subtotal (" + subtotal + ")");
        }

        List<BigDecimal> shares = allocate(saleDiscount, lineTotals, subtotal);

        List<LineResult> results = new ArrayList<>();
        BigDecimal vatTotal = Money.ZERO;
        BigDecimal total = Money.ZERO;

        for (int i = 0; i < inputs.size(); i++) {
            BigDecimal taxable = lineTotals.get(i).subtract(shares.get(i));
            BigDecimal vat = Money.percentOf(taxable, Money.nz(inputs.get(i).vatRate()));
            vatTotal = vatTotal.add(vat);
            total = total.add(taxable).add(vat);
            results.add(new LineResult(grossAmounts.get(i), lineDiscounts.get(i), lineTotals.get(i),
                    shares.get(i), taxable, vat));
        }

        return new Result(results, subtotal, saleDiscount, vatTotal, total);
    }

    private static List<BigDecimal> allocate(BigDecimal discount, List<BigDecimal> weights, BigDecimal totalWeight) {

        List<BigDecimal> shares = new ArrayList<>();

        if (discount.signum() == 0 || totalWeight.signum() == 0) {
            weights.forEach(weight -> shares.add(Money.ZERO));
            return shares;
        }

        int last = -1;
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i).signum() > 0) {
                last = i;
            }
        }

        BigDecimal allocated = Money.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal share;
            if (i == last) {
                share = discount.subtract(allocated);
            } else {
                share = discount.multiply(weights.get(i)).divide(totalWeight, 2, RoundingMode.HALF_UP);
            }
            allocated = allocated.add(share);
            shares.add(share);
        }

        return shares;
    }
}
